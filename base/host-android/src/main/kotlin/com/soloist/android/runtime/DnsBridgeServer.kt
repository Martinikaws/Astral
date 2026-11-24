package com.soloist.android.runtime

import android.content.Context
import android.net.ConnectivityManager
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Snapshot of DNS and interface-enumeration bridge activity for diagnostics and UI observability.
 */
data class DnsStats(
    val running: Boolean = false,
    val totalQueries: Long = 0L,
    val successQueries: Long = 0L,
    val failedQueries: Long = 0L,
    val ifaddrQueries: Long = 0L,
    val lastHost: String? = null,
    val lastError: String? = null,
)

/**
 * Abstract AF_UNIX DNS & `getifaddrs()` bridge server matching `native-shims/bridge_proto.h`.
 *
 * Native glibc calls to `getaddrinfo()` / `gethostbyname*()` and `getifaddrs()` are
 * intercepted by `libgandroid_shim.so` and forwarded over [socketName] in the Linux
 * abstract socket namespace so Android's `InetAddress.getAllByName()` resolves DNS via
 * netd and `ConnectivityManager` / `NetworkInterface` expose real device interfaces.
 */
class DnsBridgeServer(
    context: Context,
    val socketName: String,
) : Closeable {

    private val appContext = context.applicationContext

    private val lifecycleLock = Any()
    private val running = AtomicBoolean(false)

    private val _stats = MutableStateFlow(DnsStats())
    val stats: StateFlow<DnsStats> = _stats.asStateFlow()

    @Volatile
    private var serverSocket: LocalServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    @Volatile
    private var scope: CoroutineScope? = null

    /**
     * Binds [socketName] synchronously and begins accepting DNS resolution requests.
     * Calling [start] while already running is a no-op.
     */
    fun start() {
        synchronized(lifecycleLock) {
            if (running.get()) {
                return
            }
            val bound = LocalServerSocket(socketName)
            val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            serverSocket = bound
            scope = newScope
            running.set(true)
            _stats.update { it.copy(running = true, lastError = null) }

            acceptThread = Thread(
                { acceptLoop(bound, newScope) },
                "SoloistDnsAccept",
            ).apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "DNS bridge listening on abstract socket @$socketName")
        }
    }

    private fun acceptLoop(bound: LocalServerSocket, ownerScope: CoroutineScope) {
        try {
            while (running.get() && ownerScope.isActive) {
                val client = try {
                    bound.accept()
                } catch (e: IOException) {
                    if (running.get()) {
                        Log.w(TAG, "accept() failed on @$socketName", e)
                        _stats.update { it.copy(lastError = e.message ?: "accept failed") }
                    }
                    break
                }
                if (runCatching { client.peerCredentials.uid }.getOrNull() != android.os.Process.myUid()) {
                    runCatching { client.close() }
                    continue
                }
                if (!running.get() || !ownerScope.isActive) {
                    runCatching { client.close() }
                    break
                }
                ownerScope.launch {
                    handleClient(client)
                }
            }
        } finally {
            runCatching { bound.close() }
        }
    }

    private fun handleClient(client: LocalSocket) {
        var hostForStats: String? = null
        try {
            client.use { socket ->
                runCatching { socket.soTimeout = SOCKET_TIMEOUT_MS }
                val dis = DataInputStream(socket.inputStream)
                val out = DataOutputStream(BufferedOutputStream(socket.outputStream))

                val hostLen = dis.readUnsignedShort()
                if (hostLen == REQ_IFADDRS) {
                    hostForStats = "<ifaddrs>"
                    val records = collectInterfaceAddresses()
                    writeIfAddrsSuccessResponse(out, records)
                    recordIfAddrSuccess()
                    return
                }
                if (hostLen !in 1..MAX_HOST_LEN) {
                    recordFailure(host = null, err = "Invalid host_len: $hostLen")
                    writeErrorResponse(out, STATUS_NONAME)
                    return
                }

                val hostBytes = ByteArray(hostLen)
                dis.readFully(hostBytes)
                val host = String(hostBytes, Charsets.UTF_8)
                hostForStats = host

                if (host.isBlank()) {
                    recordFailure(host = host, err = "Blank hostname")
                    writeErrorResponse(out, STATUS_NONAME)
                    return
                }

                val resolved: List<InetAddress> = try {
                    InetAddress.getAllByName(host)
                        .filter { it is Inet4Address || it is Inet6Address }
                        .take(MAX_ADDRS)
                } catch (_: UnknownHostException) {
                    recordFailure(host = host, err = "UnknownHostException: $host")
                    writeErrorResponse(out, STATUS_NONAME)
                    return
                } catch (_: IllegalArgumentException) {
                    recordFailure(host = host, err = "Invalid hostname: $host")
                    writeErrorResponse(out, STATUS_NONAME)
                    return
                } catch (e: SecurityException) {
                    recordFailure(host = host, err = "SecurityException: ${e.message}")
                    writeErrorResponse(out, STATUS_AGAIN)
                    return
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    recordFailure(host = host, err = "${e.javaClass.simpleName}: ${e.message}")
                    writeErrorResponse(out, STATUS_AGAIN)
                    return
                }

                if (resolved.isEmpty()) {
                    recordFailure(host = host, err = "Empty DNS result: $host")
                    writeErrorResponse(out, STATUS_NONAME)
                    return
                }

                writeSuccessResponse(out, resolved)
                recordSuccess(host)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: EOFException) {
            recordFailure(host = hostForStats, err = "Client closed before request completed")
        } catch (e: IOException) {
            if (running.get()) {
                Log.w(TAG, "DNS client I/O error for host=$hostForStats", e)
                recordFailure(host = hostForStats, err = e.message ?: "I/O error")
            }
        }
    }

    private class IfAddrRecord(
        val nameBytes: ByteArray,
        val flags: Int,
        val family: Int,
        val prefixLen: Int,
        val addrBytes: ByteArray,
        val scopeId: Int,
    )

    private data class IfAddrKey(
        val name: String,
        val family: Int,
        val addr: ByteBuffer,
    )

    private fun collectInterfaceAddresses(): List<IfAddrRecord> {
        val records = ArrayList<IfAddrRecord>(8)
        val seen = HashSet<IfAddrKey>()

        // 1. Always include loopback IPv4 and IPv6
        tryAddIfAddr(
            records = records,
            seen = seen,
            ifName = "lo",
            addr = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)),
            rawPrefix = 8,
            fallbackScopeId = 0,
        )
        tryAddIfAddr(
            records = records,
            seen = seen,
            ifName = "lo",
            addr = InetAddress.getByAddress(ByteArray(16).apply { this[15] = 1 }),
            rawPrefix = 128,
            fallbackScopeId = 0,
        )

        // 2. Query active network LinkProperties via ConnectivityManager (no deprecated allNetworks)
        runCatching {
            val cm = appContext.getSystemService(ConnectivityManager::class.java) ?: return@runCatching
            val lp = cm.activeNetwork?.let { cm.getLinkProperties(it) } ?: return@runCatching
            val ifName = lp.interfaceName?.takeIf { it.isNotBlank() } ?: "wlan0"
            for (la in lp.linkAddresses) {
                if (records.size >= MAX_IFADDR_RECORDS) break
                val addr = la.address
                if (addr.isLoopbackAddress) continue
                tryAddIfAddr(
                    records = records,
                    seen = seen,
                    ifName = ifName,
                    addr = addr,
                    rawPrefix = la.prefixLength,
                    fallbackScopeId = 0,
                )
            }
        }

        // 3. Merge any additional active non-loopback addresses from NetworkInterface
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements() && records.size < MAX_IFADDR_RECORDS) {
                val ni = interfaces.nextElement()
                val activeNi = runCatching {
                    if (ni.isLoopback || !ni.isUp) null else ni
                }.getOrNull() ?: continue
                val ifName = activeNi.name?.takeIf { it.isNotBlank() } ?: continue
                for (ia in activeNi.interfaceAddresses) {
                    if (records.size >= MAX_IFADDR_RECORDS) break
                    val addr = ia.address ?: continue
                    if (addr.isLoopbackAddress) continue
                    val fallbackScope = if (addr is Inet6Address && addr.isLinkLocalAddress) {
                        runCatching { activeNi.index }.getOrDefault(0)
                    } else {
                        0
                    }
                    tryAddIfAddr(
                        records = records,
                        seen = seen,
                        ifName = ifName,
                        addr = addr,
                        rawPrefix = ia.networkPrefixLength.toInt(),
                        fallbackScopeId = fallbackScope,
                    )
                }
            }
        }

        return records
    }

    private fun tryAddIfAddr(
        records: MutableList<IfAddrRecord>,
        seen: MutableSet<IfAddrKey>,
        ifName: String,
        addr: InetAddress,
        rawPrefix: Int,
        fallbackScopeId: Int,
    ) {
        if (records.size >= MAX_IFADDR_RECORDS) return
        val cleanName = ifName.trim()
        if (cleanName.isEmpty()) return

        var nameBytes = cleanName.toByteArray(Charsets.UTF_8)
        if (nameBytes.size > MAX_IF_NAME_LEN) {
            nameBytes = nameBytes.copyOf(MAX_IF_NAME_LEN)
        }
        val keyName = String(nameBytes, Charsets.UTF_8)
        val rawBytes = addr.address

        val family: Int
        val prefixLen: Int
        val flags: Int
        val scopeId: Int
        when (addr) {
            is Inet4Address -> {
                if (rawBytes.size != 4) return
                family = AF_INET
                prefixLen = if (rawPrefix in 0..32) rawPrefix else 24
                flags = if (addr.isLoopbackAddress) {
                    IFF_UP or IFF_LOOPBACK or IFF_RUNNING
                } else {
                    IFF_UP or IFF_RUNNING or IFF_BROADCAST or IFF_MULTICAST
                }
                scopeId = 0
            }
            is Inet6Address -> {
                if (rawBytes.size != 16) return
                family = AF_INET6
                prefixLen = if (rawPrefix in 0..128) rawPrefix else 64
                flags = if (addr.isLoopbackAddress) {
                    IFF_UP or IFF_LOOPBACK or IFF_RUNNING
                } else {
                    IFF_UP or IFF_RUNNING or IFF_MULTICAST
                }
                scopeId = addr.scopeId.let { if (it != 0) it else fallbackScopeId }
            }
            else -> return
        }

        if (!seen.add(IfAddrKey(keyName, family, ByteBuffer.wrap(rawBytes)))) {
            return
        }
        records += IfAddrRecord(
            nameBytes = nameBytes,
            flags = flags,
            family = family,
            prefixLen = prefixLen,
            addrBytes = rawBytes,
            scopeId = scopeId,
        )
    }

    private fun writeIfAddrsSuccessResponse(out: DataOutputStream, records: List<IfAddrRecord>) {
        val baos = ByteArrayOutputStream(6 + records.size * 48)
        val frame = DataOutputStream(baos)
        frame.writeInt(STATUS_OK)
        frame.writeShort(records.size)
        for (rec in records) {
            frame.writeByte(rec.nameBytes.size)
            frame.write(rec.nameBytes)
            frame.writeInt(rec.flags)
            frame.writeByte(rec.family)
            frame.writeByte(rec.prefixLen)
            frame.write(rec.addrBytes)
            frame.writeInt(rec.scopeId)
        }
        frame.flush()
        out.write(baos.toByteArray())
        out.flush()
    }

    private fun writeSuccessResponse(out: DataOutputStream, addrs: List<InetAddress>) {
        val baos = ByteArrayOutputStream(6 + addrs.size * 17)
        val frame = DataOutputStream(baos)
        frame.writeInt(STATUS_OK)
        frame.writeShort(addrs.size)
        for (addr in addrs) {
            val raw = addr.address
            when (addr) {
                is Inet4Address -> {
                    frame.writeByte(AF_INET)
                    frame.write(raw, 0, 4)
                }
                is Inet6Address -> {
                    frame.writeByte(AF_INET6)
                    frame.write(raw, 0, 16)
                }
            }
        }
        frame.flush()
        out.write(baos.toByteArray())
        out.flush()
    }

    private fun writeErrorResponse(out: DataOutputStream, status: Int) {
        out.writeInt(status)
        out.writeShort(0)
        out.flush()
    }

    private fun recordIfAddrSuccess() {
        _stats.update { cur ->
            cur.copy(
                running = running.get(),
                totalQueries = cur.totalQueries + 1L,
                successQueries = cur.successQueries + 1L,
                ifaddrQueries = cur.ifaddrQueries + 1L,
                lastError = null,
            )
        }
    }

    private fun recordSuccess(host: String) {
        _stats.update { cur ->
            cur.copy(
                running = running.get(),
                totalQueries = cur.totalQueries + 1L,
                successQueries = cur.successQueries + 1L,
                lastHost = host,
                lastError = null,
            )
        }
    }

    private fun recordFailure(host: String?, err: String) {
        _stats.update { cur ->
            cur.copy(
                running = running.get(),
                totalQueries = cur.totalQueries + 1L,
                failedQueries = cur.failedQueries + 1L,
                lastHost = host ?: cur.lastHost,
                lastError = err,
            )
        }
    }

    /**
     * Stops the accept loop, cancels in-flight DNS coroutines, and closes the server socket.
     */
    override fun close() {
        synchronized(lifecycleLock) {
            if (!running.getAndSet(false) && serverSocket == null) {
                return
            }
            val sock = serverSocket
            serverSocket = null
            val acc = acceptThread
            acceptThread = null
            val activeScope = scope
            scope = null

            runCatching {
                LocalSocket().use { wake ->
                    wake.connect(LocalSocketAddress(socketName))
                }
            }
            runCatching { sock?.close() }
            if (acc != null && acc !== Thread.currentThread()) {
                runCatching { acc.join(2000L) }
            }
            runCatching { activeScope?.cancel() }
            _stats.update { it.copy(running = false) }
            Log.i(TAG, "DNS bridge stopped (@$socketName)")
        }
    }

    companion object {
        private const val TAG = "DnsBridgeServer"

        private const val REQ_IFADDRS = 0
        private const val MAX_HOST_LEN = 1024
        private const val MAX_ADDRS = 128
        private const val MAX_IFADDR_RECORDS = 64
        private const val MAX_IF_NAME_LEN = 31
        private const val SOCKET_TIMEOUT_MS = 5000

        private const val AF_INET = 4
        private const val AF_INET6 = 6

        private const val IFF_UP = 0x1
        private const val IFF_BROADCAST = 0x2
        private const val IFF_LOOPBACK = 0x8
        private const val IFF_RUNNING = 0x40
        private const val IFF_MULTICAST = 0x1000

        private const val STATUS_OK = 0
        private const val STATUS_NONAME = 1
        private const val STATUS_AGAIN = 2
    }
}
