package com.soloist.android.runtime

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.myspoti.host.android.Logbook

/** What Soloist prints when it wants to be announced: `ZeroConf service on port 41234 path /zc`. */
data class ZeroConfLine(val port: Int, val cpath: String)

private val ZEROCONF_LINE = Regex("""ZeroConf service on port (\d+) path (\S+)""")

/** The announcement [line] asks for, or null for any other line. */
fun parseZeroConfLine(line: String): ZeroConfLine? {
    val m = ZEROCONF_LINE.find(line) ?: return null
    val port = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
    return ZeroConfLine(port, m.groupValues[2])
}

/** The slice of NsdManager [ZeroConf] needs, so the decision logic runs in plain JVM tests. */
interface ServiceRegistrar {
    /** Registers the service; [onResult] gets the name actually used (the system renames on a clash) or null on failure. */
    fun register(name: String, port: Int, attributes: Map<String, String>, onResult: (String?) -> Unit): Any
    fun unregister(handle: Any)
}

/**
 * Announces `_spotify-connect._tcp` ourselves: Soloist's own mDNS finds no IPv4 inside the app
 * sandbox, so without this the phone never lists the device. At most one announcement is live.
 */
class ZeroConf(
    private val registrar: ServiceRegistrar,
    private val log: (String) -> Unit = { Logbook.log("soloist: $it") },
) {
    private data class Key(val name: String, val port: Int, val cpath: String)

    private var key: Key? = null
    private var handle: Any? = null

    /** Same arguments again is a no-op; different ones replace the live announcement. */
    @Synchronized
    fun announce(name: String, port: Int, cpath: String) {
        val next = Key(name, port, cpath)
        if (next == key) return
        withdraw()
        key = next
        try {
            handle = registrar.register(name, port, mapOf("CPath" to cpath, "VERSION" to "1.0", "Stack" to "SP")) { used ->
                log(if (used == null) "zeroconf registration failed for $name:$port" else "zeroconf announced as '$used' on port $port")
            }
        } catch (e: Exception) {
            key = null
            log("zeroconf register threw: ${e.message}")
        }
    }

    @Synchronized
    fun withdraw() {
        val h = handle
        handle = null
        key = null
        if (h != null) {
            try {
                registrar.unregister(h)
                log("zeroconf withdrawn")
            } catch (e: Exception) {
                log("zeroconf unregister threw: ${e.message}")
            }
        }
    }
}

class NsdServiceRegistrar(context: Context) : ServiceRegistrar {
    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    override fun register(name: String, port: Int, attributes: Map<String, String>, onResult: (String?) -> Unit): Any {
        val info = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_spotify-connect._tcp"
            this.port = port
            attributes.forEach { (k, v) -> setAttribute(k, v) }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(i: NsdServiceInfo) = onResult(i.serviceName)
            override fun onRegistrationFailed(i: NsdServiceInfo, code: Int) = onResult(null)
            override fun onServiceUnregistered(i: NsdServiceInfo) {}
            override fun onUnregistrationFailed(i: NsdServiceInfo, code: Int) {}
        }
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return listener
    }

    override fun unregister(handle: Any) {
        nsd.unregisterService(handle as NsdManager.RegistrationListener)
    }
}
