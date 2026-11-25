package com.soloist.android.runtime

import android.content.Context
import android.os.Process as AndroidProcess
import android.util.Log
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Lifecycle state of the native `libsoloist.so` daemon process.
 */
sealed interface DaemonState {
    data object Stopped : DaemonState
    data class Starting(val pairMode: Boolean) : DaemonState
    data class Running(val wsPort: Int, val pairMode: Boolean) : DaemonState
    data class Error(val message: String) : DaemonState
}

/**
 * Result of a one-shot native CLI invocation (such as `setup-api-key`).
 */
data class CommandResult(
    val exitCode: Int,
    val output: String,
    val timedOut: Boolean = false,
    val error: String? = null,
) {
    val success: Boolean get() = exitCode == 0 && !timedOut && error == null
}

/**
 * Individual self-test check result.
 */
data class SelfTestCheck(
    val name: String,
    val passed: Boolean,
    val exitCode: Int?,
    val durationMs: Long,
    val summary: String,
    val output: String,
)

/**
 * Aggregate report produced by [SoloistProcessManager.runSelfTest].
 */
data class SelfTestReport(
    val checks: List<SelfTestCheck>,
    val passedCount: Int,
    val totalCount: Int,
    val allPassed: Boolean,
    val summaryLine: String,
)

/**
 * Manages the ARM64 glibc + `libgandroid_shim.so` runtime environment, DNS/audio
 * bridge servers, API-key setup, `libsoloist.so` daemon lifecycle, and self-tests.
 */
class SoloistProcessManager(
    context: Context,
) : Closeable {

    private val appContext = context.applicationContext

    val filesDir: File = appContext.filesDir
    val cacheDir: File = appContext.cacheDir

    val cacertFile: File = File(filesDir, "cacert.pem")
    val machineIdFile: File = File(filesDir, "machine-id")
    val dataDir: File = File(filesDir, "soloist-data")
    val homeDir: File = File(filesDir, "soloist-home")
    val configDir: File = File(filesDir, "soloist-config")
    val cacheDirPath: File = File(cacheDir, "soloist-cache")
    val tmpDir: File = File(cacheDir, "tmp")

    val nativeLibDir: String = appContext.applicationInfo.nativeLibraryDir

    val dnsSocketName: String = "soloist.dns.${AndroidProcess.myPid()}"
    val audioSocketName: String = "soloist.audio.${AndroidProcess.myPid()}"

    @Volatile
    var onPlaybackHeadAdvance: ((bytesPlayed: Long, bytesWritten: Long, playing: Boolean) -> Unit)? = null

    val dnsBridge: DnsBridgeServer = DnsBridgeServer(appContext, dnsSocketName)
    val audioBridge: AudioBridgeServer = AudioBridgeServer(audioSocketName) { played, written, playing ->
        onPlaybackHeadAdvance?.invoke(played, written, playing)
    }

    val dnsStats: StateFlow<DnsStats> = dnsBridge.stats
    val audioStats: StateFlow<AudioStreamStats> = audioBridge.stats

    private val _daemonState = MutableStateFlow<DaemonState>(DaemonState.Stopped)
    val daemonState: StateFlow<DaemonState> = _daemonState.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val logLock = Any()
    private val logBuffer = ArrayDeque<String>(MAX_LOG_LINES)

    private val opMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var daemonProcess: Process? = null

    private var logJob: Job? = null
    private var portPollJob: Job? = null
    private var monitorJob: Job? = null
    private val stopRequested = AtomicBoolean(false)

    private val zeroConf = ZeroConf(NsdServiceRegistrar(appContext))

    init {
        ensureRuntimeFiles()
    }

    /**
     * Ensures required runtime directories, `cacert.pem`, and `machine-id` exist on disk.
     */
    fun ensureRuntimeFiles() {
        dataDir.mkdirs()
        homeDir.mkdirs()
        configDir.mkdirs()
        cacheDirPath.mkdirs()
        tmpDir.mkdirs()

        ensureCaCertExtracted()
        ensureMachineIdGenerated()
    }

    private fun ensureCaCertExtracted() {
        val assetBytes = appContext.assets.open(CACERT_ASSET_NAME).use { it.readBytes() }
        if (cacertFile.isFile && cacertFile.length() == assetBytes.size.toLong()) {
            return
        }
        val tmp = File(filesDir, ".cacert.pem.tmp.${AndroidProcess.myPid()}")
        try {
            tmp.writeBytes(assetBytes)
            if (!tmp.renameTo(cacertFile)) {
                cacertFile.writeBytes(assetBytes)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun ensureMachineIdGenerated() {
        if (isValidMachineIdFile(machineIdFile)) {
            return
        }
        val randomBytes = ByteArray(16)
        SecureRandom().nextBytes(randomBytes)
        val hex = buildString(33) {
            for (b in randomBytes) {
                append(HEX_CHARS[(b.toInt() ushr 4) and 0x0F])
                append(HEX_CHARS[b.toInt() and 0x0F])
            }
            append('\n')
        }
        val tmp = File(filesDir, ".machine-id.tmp.${AndroidProcess.myPid()}")
        try {
            tmp.writeText(hex, Charsets.US_ASCII)
            if (!tmp.renameTo(machineIdFile)) {
                machineIdFile.writeText(hex, Charsets.US_ASCII)
            }
        } finally {
            tmp.delete()
        }
    }

    private fun isValidMachineIdFile(file: File): Boolean {
        if (!file.isFile || file.length() < 32L || file.length() > 64L) {
            return false
        }
        val content = runCatching { file.readText(Charsets.US_ASCII).trim() }.getOrNull() ?: return false
        return content.length == 32 && content.all { it in '0'..'9' || it in 'a'..'f' }
    }

    /**
     * Builds the `libgld_aarch64.so` command line launching [binarySoName] with
     * `libgandroid_shim.so` preloaded.
     */
    fun buildGlibcCommand(binarySoName: String, args: List<String> = emptyList()): List<String> {
        return listOf(
            "$nativeLibDir/libgld_aarch64.so",
            "--inhibit-cache",
            "--library-path",
            nativeLibDir,
            "--preload",
            "$nativeLibDir/libgandroid_shim.so",
            "$nativeLibDir/$binarySoName",
        ) + args
    }

    /**
     * Builds the environment variables required by glibc, OpenSSL, and native shims.
     */
    fun buildEnvironment(
        dnsSocket: String = dnsSocketName,
        audioSocket: String = audioSocketName,
    ): Map<String, String> {
        return linkedMapOf(
            "HOME" to homeDir.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "TEMPDIR" to tmpDir.absolutePath,
            "XDG_DATA_HOME" to dataDir.absolutePath,
            "XDG_CONFIG_HOME" to configDir.absolutePath,
            "XDG_CACHE_HOME" to cacheDirPath.absolutePath,
            "SPOTIFY_SSL_CERT_FILE" to cacertFile.absolutePath,
            "SSL_CERT_FILE" to cacertFile.absolutePath,
            "SOLOIST_DNS_SOCK" to dnsSocket,
            "SOLOIST_AUDIO_SOCK" to audioSocket,
            "SOLOIST_MACHINE_ID_FILE" to machineIdFile.absolutePath,
            "LC_ALL" to "C.UTF-8",
            "LANG" to "C.UTF-8",
        )
    }

    /**
     * Non-destructively checks whether `credentials/api_key` (or a legacy API key file) exists inside [dataDir].
     */
    internal fun reportStartFailure(error: Exception) {
        _daemonState.value = DaemonState.Error(error.message ?: "Soloist could not start")
    }

    fun hasSavedApiKey(): Boolean {
        return savedApiKeyFiles().isNotEmpty()
    }

    /**
     * Non-destructively checks whether Spotify Connect session/credential files exist inside
     * [dataDir]/credentials or [dataDir]/settings.
     */
    fun hasStoredCredentials(): Boolean {
        if (!dataDir.isDirectory) return false
        return listOf(
            File(dataDir, CREDENTIALS_DIRNAME),
            File(dataDir, SETTINGS_DIRNAME),
        ).any { dir ->
            dir.isDirectory && dir.walkTopDown()
                .maxDepth(4)
                .any { file ->
                    if (!file.isFile || file.length() <= 0L) {
                        return@any false
                    }
                    val name = file.name
                    name != API_KEY_FILENAME &&
                        name != LEGACY_API_KEY_FILENAME &&
                        !name.contains("api_key", ignoreCase = true) &&
                        !name.endsWith(".lock") &&
                        !name.endsWith(".tmp")
                }
        }
    }

    private fun clientTokenFiles(): List<File> = listOf(
        File(File(dataDir, CACHE_DIRNAME), CLIENT_TOKEN_FILENAME),
        File(cacheDirPath, CLIENT_TOKEN_FILENAME),
    )

    private fun savedApiKeyFiles(): List<File> {
        if (!dataDir.isDirectory) return emptyList()
        val credentialsDir = File(dataDir, CREDENTIALS_DIRNAME)
        val candidates = listOf(
            File(credentialsDir, API_KEY_FILENAME),
            File(dataDir, LEGACY_API_KEY_FILENAME),
            File(dataDir, API_KEY_FILENAME),
        )
        val discovered = listOf(credentialsDir, dataDir).flatMap { dir ->
            if (!dir.isDirectory) {
                emptyList()
            } else {
                dir.listFiles()?.filter { file ->
                    file.isFile && file.name.contains("api_key", ignoreCase = true) && file.length() > 0L
                }.orEmpty()
            }
        }
        return (candidates + discovered)
            .filter { it.isFile && it.length() > 0L }
            .distinctBy { it.absolutePath }
    }

    private fun clearCachedClientTokens(tag: String, reason: String) {
        for (file in clientTokenFiles()) {
            if (!file.isFile) continue
            deleteClientTokenFile(
                file = file,
                logMessage = "$tag Cleared cached client_token ($reason): ${file.absolutePath}",
                appendMessage = "$tag Cleared cached client_token ($reason)",
                warnMessage = "$tag Failed to clear cached client_token ($reason): ${file.absolutePath}",
            )
        }
    }

    private fun clearStaleClientTokensBeforeStart() {
        val newestApiKeyMtime = savedApiKeyFiles().maxOfOrNull { it.lastModified() } ?: return
        for (tokenFile in clientTokenFiles()) {
            if (tokenFile.isFile && tokenFile.lastModified() < newestApiKeyMtime) {
                deleteClientTokenFile(
                    file = tokenFile,
                    logMessage = "[daemon] Cleared stale client_token older than saved API key: ${tokenFile.absolutePath}",
                    appendMessage = "[daemon] Cleared stale cached client_token (older than saved API key)",
                    warnMessage = "[daemon] Failed to clear stale client_token older than saved API key: ${tokenFile.absolutePath}",
                )
            }
        }
    }

    private fun deleteClientTokenFile(
        file: File,
        logMessage: String,
        appendMessage: String,
        warnMessage: String,
    ) {
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        if (deleted) {
            Log.i(TAG, logMessage)
            appendLog(appendMessage)
        } else if (file.exists()) {
            Log.w(TAG, warnMessage)
        }
    }

    /**
     * Persists the Spotify/Soloist API key via `libsoloist.so setup-api-key`.
     * Refuses if the daemon is running so `dataDir` locks do not conflict, and never logs [apiKey].
     */
    suspend fun setupApiKey(apiKey: String): CommandResult = withContext(Dispatchers.IO) {
        val trimmed = apiKey.trim()
        if (trimmed.isEmpty()) {
            return@withContext CommandResult(
                exitCode = -1,
                output = "",
                error = "API key must not be empty",
            )
        }

        opMutex.withLock {
            if (isDaemonActiveLocked()) {
                return@withLock CommandResult(
                    exitCode = -1,
                    output = "",
                    error = "Cannot update API key while Soloist daemon is running",
                )
            }

            ensureRuntimeFiles()
            val cmd = buildGlibcCommand(
                binarySoName = SOLOIST_SO,
                args = listOf("setup-api-key", "--data-dir", dataDir.absolutePath),
            )

            val process = try {
                ProcessBuilder(cmd)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(buildEnvironment()) }
                    .start()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch setup-api-key", e)
                return@withLock CommandResult(
                    exitCode = -1,
                    output = "",
                    error = e.message ?: "Failed to launch setup-api-key",
                )
            }

            try {
                process.outputStream.use { stdin ->
                    stdin.write((trimmed + "\n").toByteArray(Charsets.UTF_8))
                    stdin.flush()
                }

                val outputBuffer = StringBuilder()
                val readerThread = spawnLineCollector(process.inputStream, "SoloistSetupApiKeyReader") { line ->
                    outputBuffer.appendLine(line)
                }

                val finished = process.waitFor(SETUP_API_KEY_TIMEOUT_SEC, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    process.waitFor(1, TimeUnit.SECONDS)
                    runCatching { readerThread.join(500L) }
                    return@withLock CommandResult(
                        exitCode = -1,
                        output = outputBuffer.toString().trim(),
                        timedOut = true,
                        error = "Timed out waiting for setup-api-key",
                    )
                }

                runCatching { readerThread.join(1000L) }
                val exitCode = process.exitValue()
                if (exitCode == 0) {
                    clearCachedClientTokens("[setup-api-key]", "API key updated")
                }
                val out = outputBuffer.toString().trim()
                appendLog("[setup-api-key] exited with code $exitCode")
                CommandResult(
                    exitCode = exitCode,
                    output = out,
                    error = if (exitCode == 0) null else out.ifEmpty { "setup-api-key failed (exit $exitCode)" },
                )
            } catch (e: CancellationException) {
                runCatching { process.destroyForcibly() }
                throw e
            } catch (e: Exception) {
                runCatching { process.destroyForcibly() }
                Log.e(TAG, "Error running setup-api-key", e)
                CommandResult(
                    exitCode = -1,
                    output = "",
                    error = e.message ?: "setup-api-key failed",
                )
            }
        }
    }

    /**
     * Starts the DNS and audio bridges and launches the `libsoloist.so` daemon.
     * Invokes [onWsPortReady] once as soon as the WebSocket port is discovered via log or `ws.port`.
     */
    suspend fun startDaemon(
        deviceName: String,
        initialVolume: Int,
        pairMode: Boolean,
        onWsPortReady: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        opMutex.withLock {
            if (isDaemonActiveLocked()) {
                stopDaemonLocked()
            }

            ensureRuntimeFiles()
            stopRequested.set(false)
            zeroConf.withdraw()
            _daemonState.value = DaemonState.Starting(pairMode)

            val process = try {
                dnsBridge.start()
                audioBridge.start()

                File(dataDir, WS_PORT_FILENAME).delete()
                File(dataDir, WS_ADDR_FILENAME).delete()
                clearStaleClientTokensBeforeStart()

                val args = mutableListOf(
                    "--device-name",
                    deviceName,
                    "--data-dir",
                    dataDir.absolutePath,
                    "--cache-dir",
                    cacheDirPath.absolutePath,
                    "--ws",
                    "127.0.0.1:0",
                    "--initial-volume",
                    initialVolume.coerceIn(0, 100).toString(),
                    "--verbose",
                )
                if (pairMode) {
                    args += "--pair"
                }

                val cmd = buildGlibcCommand(SOLOIST_SO, args)
                appendLog("[daemon] Starting Soloist (pairMode=$pairMode, deviceName=$deviceName)")

                ProcessBuilder(cmd)
                    .directory(filesDir)
                    .redirectErrorStream(true)
                    .apply { environment().putAll(buildEnvironment()) }
                    .start()
            } catch (e: CancellationException) {
                runCatching { audioBridge.close() }
                runCatching { dnsBridge.close() }
                _daemonState.value = DaemonState.Stopped
                throw e
            } catch (e: Exception) {
                runCatching { audioBridge.close() }
                runCatching { dnsBridge.close() }
                val msg = "Failed to spawn libsoloist.so: ${e.message}"
                Log.e(TAG, msg, e)
                appendLog("[daemon] ERROR: $msg")
                _daemonState.value = DaemonState.Error(msg)
                return@withLock
            }

            runCatching { process.outputStream.close() }
            daemonProcess = process

            val wsPortNotified = AtomicBoolean(false)
            val notifyPortOnce: (Int) -> Unit = { port ->
                if (port in 1..65535 && wsPortNotified.compareAndSet(false, true)) {
                    _daemonState.value = DaemonState.Running(port, pairMode)
                    appendLog("[daemon] WebSocket listening on 127.0.0.1:$port")
                    runCatching { onWsPortReady(port) }
                        .onFailure { Log.e(TAG, "onWsPortReady callback threw", it) }
                }
            }

            val readerJob = scope.launch {
                try {
                    readStreamLines(process.inputStream, shouldContinue = { isActive }) { text ->
                        // Keep verbose daemon output inside the app.
                        appendLog(text)
                        app.myspoti.host.android.Logbook.log("soloist: $text")
                        WS_PORT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
                            notifyPortOnce(port)
                        }
                        parseZeroConfLine(text)?.let { zeroConf.announce(deviceName, it.port, it.cpath) }
                    }
                } catch (e: Exception) {
                    if (isActive && !stopRequested.get()) {
                        Log.w(TAG, "Daemon log stream ended with error: ${e.message}")
                    }
                }
            }
            logJob = readerJob

            val pollerJob = scope.launch {
                val wsPortFile = File(dataDir, WS_PORT_FILENAME)
                while (isActive && !wsPortNotified.get() && process.isAlive) {
                    if (wsPortFile.isFile) {
                        val port = runCatching {
                            wsPortFile.readText(Charsets.UTF_8).trim().toIntOrNull()
                        }.getOrNull()
                        if (port != null && port in 1..65535) {
                            notifyPortOnce(port)
                            break
                        }
                    }
                    delay(WS_PORT_POLL_INTERVAL_MS)
                }
            }
            portPollJob = pollerJob

            monitorJob = scope.launch {
                val exitCode = runCatching { process.waitFor() }.getOrElse { -1 }
                pollerJob.cancel()
                runCatching { readerJob.join() }
                zeroConf.withdraw()

                val wasRequestedStop = stopRequested.get()
                val stillCurrentProcess = daemonProcess === process
                if (stillCurrentProcess) {
                    daemonProcess = null
                    runCatching { dnsBridge.close() }
                    runCatching { audioBridge.close() }
                }

                if (wasRequestedStop) {
                    if (stillCurrentProcess) {
                        _daemonState.value = DaemonState.Stopped
                    }
                    appendLog("[daemon] Stopped (exit=$exitCode)")
                } else if (exitCode == 0) {
                    if (stillCurrentProcess) {
                        _daemonState.value = DaemonState.Stopped
                    }
                    appendLog(
                        if (pairMode) {
                            "[daemon] Pairing run finished cleanly (exit=0)"
                        } else {
                            "[daemon] Exited cleanly (exit=0)"
                        }
                    )
                } else {
                    val errMsg = "Soloist exited unexpectedly with code $exitCode"
                    appendLog("[daemon] ERROR: $errMsg")
                    if (stillCurrentProcess) {
                        _daemonState.value = DaemonState.Error(errMsg)
                    }
                }
            }
        }
    }

    /**
     * Gracefully terminates the daemon process (forcing termination if needed) and closes bridges.
     */
    suspend fun stopDaemon() = withContext(Dispatchers.IO + NonCancellable) {
        opMutex.withLock {
            stopDaemonLocked()
        }
    }

    private suspend fun stopDaemonLocked() {
        stopRequested.set(true)
        zeroConf.withdraw()

        val proc = daemonProcess
        daemonProcess = null

        val poller = portPollJob
        portPollJob = null
        poller?.cancel()

        val monitor = monitorJob
        monitorJob = null

        if (proc != null) {
            runCatching { proc.destroy() }
            val exited = runCatching { proc.waitFor(1500L, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            if (!exited && proc.isAlive) {
                runCatching { proc.destroyForcibly() }
                runCatching { proc.waitFor(500L, TimeUnit.MILLISECONDS) }
            }
        }

        val reader = logJob
        logJob = null
        runCatching { reader?.cancelAndJoin() }
        runCatching { monitor?.cancelAndJoin() }

        runCatching { dnsBridge.close() }
        runCatching { audioBridge.close() }
        _daemonState.value = DaemonState.Stopped
    }

    /**
     * Runs the 4-stage runtime self-test (`--version`, `--help`, `libgselftest.so`, and an isolated
     * startup probe) using temporary bridge instances if the daemon is not already running, or
     * isolated test sockets when a daemon is active.
     */
    suspend fun runSelfTest(): SelfTestReport = withContext(Dispatchers.IO) {
        opMutex.withLock {
            ensureRuntimeFiles()

            val daemonActive = isDaemonActiveLocked()
            val testDnsSocket: String
            val testAudioSocket: String
            val tempDnsBridge: DnsBridgeServer
            val tempAudioBridge: AudioBridgeServer

            if (!daemonActive) {
                testDnsSocket = dnsSocketName
                testAudioSocket = audioSocketName
                tempDnsBridge = dnsBridge
                tempAudioBridge = audioBridge
            } else {
                val seq = selfTestSeq.incrementAndGet()
                testDnsSocket = "soloist.dns.selftest.${AndroidProcess.myPid()}.$seq"
                testAudioSocket = "soloist.audio.selftest.${AndroidProcess.myPid()}.$seq"
                tempDnsBridge = DnsBridgeServer(appContext, testDnsSocket)
                tempAudioBridge = AudioBridgeServer(testAudioSocket)
            }

            try {
                tempDnsBridge.start()
                tempAudioBridge.start()
                val env = buildEnvironment(dnsSocket = testDnsSocket, audioSocket = testAudioSocket)
                val checks = mutableListOf<SelfTestCheck>()

                checks += runOneShotCheck(
                    name = "1/4 soloist --version",
                    binarySoName = SOLOIST_SO,
                    args = listOf("--version"),
                    env = env,
                    timeoutSec = 10L,
                ) { exitCode, out, timedOut ->
                    val passed = !timedOut && exitCode == 0 && out.isNotBlank()
                    val summary = if (passed) {
                        "version: ${out.lineSequence().firstOrNull().orEmpty().trim()}"
                    } else {
                        "exit=$exitCode timedOut=$timedOut"
                    }
                    passed to summary
                }

                checks += runOneShotCheck(
                    name = "2/4 soloist --help",
                    binarySoName = SOLOIST_SO,
                    args = listOf("--help"),
                    env = env,
                    timeoutSec = 10L,
                ) { exitCode, out, timedOut ->
                    val hasMarkers = out.contains("Soloist", ignoreCase = true) ||
                        out.contains("--device-name")
                    val passed = !timedOut && exitCode == 0 && hasMarkers
                    val summary = if (passed) {
                        "help output verified (--device-name present)"
                    } else {
                        "exit=$exitCode timedOut=$timedOut markers=$hasMarkers"
                    }
                    passed to summary
                }

                checks += runOneShotCheck(
                    name = "3/4 libgselftest.so",
                    binarySoName = SELFTEST_SO,
                    args = emptyList(),
                    env = env,
                    timeoutSec = 20L,
                ) { exitCode, out, timedOut ->
                    val passed = !timedOut && exitCode == 0 && out.contains("[selftest] ALL CHECKS PASSED")
                    val summary = if (passed) {
                        "DNS + ifaddrs + machine-id + dlopen(pulse) + AudioTrack passed"
                    } else {
                        "exit=$exitCode timedOut=$timedOut"
                    }
                    passed to summary
                }

                checks += runStartupProbeCheck(env)

                val passedCount = checks.count { it.passed }
                val summaryLine = "[SELFTEST_SUMMARY] passed=$passedCount/${checks.size}"
                Log.i(SELFTEST_TAG, summaryLine)
                appendLog(summaryLine)

                SelfTestReport(
                    checks = checks,
                    passedCount = passedCount,
                    totalCount = checks.size,
                    allPassed = passedCount == checks.size,
                    summaryLine = summaryLine,
                )
            } finally {
                runCatching { tempAudioBridge.close() }
                runCatching { tempDnsBridge.close() }
            }
        }
    }

    private fun runOneShotCheck(
        name: String,
        binarySoName: String,
        args: List<String>,
        env: Map<String, String>,
        timeoutSec: Long,
        evaluate: (exitCode: Int, output: String, timedOut: Boolean) -> Pair<Boolean, String>,
    ): SelfTestCheck {
        val startedMs = System.currentTimeMillis()
        val cmd = buildGlibcCommand(binarySoName, args)
        val outBuf = StringBuilder()
        var exitCode: Int? = null
        var timedOut = false

        try {
            val proc = ProcessBuilder(cmd)
                .directory(filesDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()

            runCatching { proc.outputStream.close() }
            val readerThread = spawnLineCollector(proc.inputStream, "SoloistSelfTestReader") { line ->
                outBuf.appendLine(line)
            }

            val finished = proc.waitFor(timeoutSec, TimeUnit.SECONDS)
            if (!finished) {
                timedOut = true
                proc.destroyForcibly()
                proc.waitFor(1, TimeUnit.SECONDS)
            } else {
                exitCode = proc.exitValue()
            }
            runCatching { readerThread.join(1000L) }
        } catch (e: Exception) {
            outBuf.appendLine("Exception: ${e.message}")
        }

        val durationMs = System.currentTimeMillis() - startedMs
        val output = outBuf.toString().trim()
        val (passed, summary) = evaluate(exitCode ?: -1, output, timedOut)
        return recordSelfTestCheck(
            name = name,
            passed = passed,
            exitCode = exitCode,
            durationMs = durationMs,
            summary = summary,
            output = output,
        )
    }

    private fun runStartupProbeCheck(env: Map<String, String>): SelfTestCheck {
        val name = "4/4 soloist startup probe"
        val startedMs = System.currentTimeMillis()
        val suffix = "${System.currentTimeMillis()}_${selfTestSeq.incrementAndGet()}"
        val probeDataDir = File(tmpDir, "probe-data-$suffix").apply { mkdirs() }
        val probeCacheDir = File(tmpDir, "probe-cache-$suffix").apply { mkdirs() }

        val outBuf = StringBuilder()
        var exitCode: Int? = null
        val wsPortRef = AtomicInteger(0)
        var proc: Process? = null

        try {
            val cmd = buildGlibcCommand(
                binarySoName = SOLOIST_SO,
                args = listOf(
                    "--api-key",
                    "selftest_probe_unauthenticated",
                    "--device-name",
                    "SoloistProbe",
                    "--data-dir",
                    probeDataDir.absolutePath,
                    "--cache-dir",
                    probeCacheDir.absolutePath,
                    "--ws",
                    "127.0.0.1:0",
                    "--verbose",
                ),
            )
            val launched = ProcessBuilder(cmd)
                .directory(filesDir)
                .redirectErrorStream(true)
                .apply { environment().putAll(env) }
                .start()
            proc = launched
            runCatching { launched.outputStream.close() }

            val readerThread = spawnLineCollector(launched.inputStream, "SoloistProbeReader") { text ->
                synchronized(outBuf) {
                    outBuf.appendLine(text)
                }
                WS_PORT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { port ->
                    if (port in 1..65535) {
                        wsPortRef.compareAndSet(0, port)
                    }
                }
            }

            val wsPortFile = File(probeDataDir, WS_PORT_FILENAME)
            fun syncWsPortFromFile() {
                if (wsPortRef.get() == 0 && wsPortFile.isFile) {
                    val filePort = runCatching {
                        wsPortFile.readText(Charsets.UTF_8).trim().toIntOrNull()
                    }.getOrNull()
                    if (filePort != null && filePort in 1..65535) {
                        wsPortRef.compareAndSet(0, filePort)
                    }
                }
            }

            val deadline = startedMs + PROBE_MAX_WAIT_MS
            val minLiveUntil = startedMs + PROBE_MIN_RUN_MS

            while (System.currentTimeMillis() < deadline) {
                syncWsPortFromFile()
                if (wsPortRef.get() != 0 && System.currentTimeMillis() >= minLiveUntil) {
                    break
                }
                if (!launched.isAlive) {
                    exitCode = runCatching { launched.exitValue() }.getOrNull()
                    break
                }
                Thread.sleep(100L)
            }

            if (launched.isAlive) {
                runCatching { launched.destroy() }
                val stopped = runCatching { launched.waitFor(750L, TimeUnit.MILLISECONDS) }.getOrDefault(false)
                if (!stopped && launched.isAlive) {
                    runCatching { launched.destroyForcibly() }
                    runCatching { launched.waitFor(500L, TimeUnit.MILLISECONDS) }
                }
            } else if (exitCode == null) {
                exitCode = runCatching { launched.exitValue() }.getOrNull()
            }
            runCatching { readerThread.join(1000L) }
            syncWsPortFromFile()
        } catch (e: Exception) {
            synchronized(outBuf) {
                outBuf.appendLine("Exception: ${e.message}")
            }
        } finally {
            runCatching {
                if (proc?.isAlive == true) {
                    proc.destroyForcibly()
                }
            }
            runCatching { probeDataDir.deleteRecursively() }
            runCatching { probeCacheDir.deleteRecursively() }
        }

        val wsPortDetected = wsPortRef.get().takeIf { it in 1..65535 }
        val durationMs = System.currentTimeMillis() - startedMs
        val output = synchronized(outBuf) { outBuf.toString().trim() }
        val fatalCrash = hasFatalProbeCrash(exitCode, output)
        val missingIpv4 = output.contains("no local IPv4 address", ignoreCase = true)
        val passed = !fatalCrash && wsPortDetected != null && !missingIpv4

        val summary = when {
            fatalCrash -> "fatal crash/linker failure (exit=${exitCode ?: "?"})"
            missingIpv4 -> "mDNS missing local IPv4 address"
            wsPortDetected == null -> "WS server did not start (exit=${exitCode ?: "?"})"
            else -> "WS port=$wsPortDetected + mDNS/TLS clean"
        }
        return recordSelfTestCheck(
            name = name,
            passed = passed,
            exitCode = exitCode,
            durationMs = durationMs,
            summary = summary,
            output = output,
        )
    }

    private fun recordSelfTestCheck(
        name: String,
        passed: Boolean,
        exitCode: Int?,
        durationMs: Long,
        summary: String,
        output: String,
    ): SelfTestCheck {
        val logMsg = "[$name] ${if (passed) "PASS" else "FAIL"} (${durationMs}ms): $summary"
        Log.i(SELFTEST_TAG, logMsg)
        appendLog(logMsg)
        if (output.isNotEmpty()) {
            Log.i(SELFTEST_TAG, "[$name] output: ${formatSelfTestOutputSnippet(output)}")
        }
        return SelfTestCheck(
            name = name,
            passed = passed,
            exitCode = exitCode,
            durationMs = durationMs,
            summary = summary,
            output = output,
        )
    }

    private fun formatSelfTestOutputSnippet(output: String): String {
        val lines = output.lines()
        val tailed = if (lines.size > MAX_SELFTEST_OUTPUT_LINES) {
            lines.takeLast(MAX_SELFTEST_OUTPUT_LINES).joinToString("\n")
        } else {
            output
        }
        return when {
            tailed.length > MAX_SELFTEST_OUTPUT_CHARS -> "..." + tailed.takeLast(MAX_SELFTEST_OUTPUT_CHARS)
            lines.size > MAX_SELFTEST_OUTPUT_LINES -> "...\n$tailed"
            else -> tailed
        }
    }

    private fun hasFatalProbeCrash(exitCode: Int?, output: String): Boolean {
        if (exitCode != null && (exitCode < 0 || exitCode in FATAL_EXIT_CODES)) {
            return true
        }
        return FATAL_OUTPUT_PATTERNS.any { pattern ->
            output.contains(pattern, ignoreCase = true)
        }
    }

    private fun isDaemonActiveLocked(): Boolean {
        val proc = daemonProcess
        if (proc != null && proc.isAlive) {
            return true
        }
        val state = _daemonState.value
        return state is DaemonState.Starting || state is DaemonState.Running
    }

    private inline fun readStreamLines(
        stream: InputStream,
        shouldContinue: () -> Boolean = { true },
        onLine: (String) -> Unit,
    ) {
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            while (shouldContinue()) {
                val line = reader.readLine() ?: break
                onLine(line)
            }
        }
    }

    private fun spawnLineCollector(
        stream: InputStream,
        threadName: String,
        onLine: (String) -> Unit,
    ): Thread {
        return Thread({
            runCatching { readStreamLines(stream, onLine = onLine) }
        }, threadName).apply {
            isDaemon = true
            start()
        }
    }

    private fun appendLog(line: String) {
        synchronized(logLock) {
            if (logBuffer.size >= MAX_LOG_LINES) {
                logBuffer.removeFirst()
            }
            logBuffer.addLast(line)
            _logs.value = logBuffer.toList()
        }
    }

    /**
     * Releases background resources by closing bridge servers and destroying any running daemon.
     */
    override fun close() {
        stopRequested.set(true)
        zeroConf.withdraw()
        portPollJob?.cancel()
        logJob?.cancel()
        monitorJob?.cancel()
        daemonProcess?.let { proc ->
            runCatching { proc.destroy() }
            val exited = runCatching { proc.waitFor(500L, TimeUnit.MILLISECONDS) }.getOrDefault(false)
            if (!exited && proc.isAlive) {
                runCatching { proc.destroyForcibly() }
            }
        }
        daemonProcess = null
        runCatching { dnsBridge.close() }
        runCatching { audioBridge.close() }
        scope.cancel()
        _daemonState.value = DaemonState.Stopped
    }

    companion object {
        private const val TAG = "SoloistProcess"
        private const val SELFTEST_TAG = "SoloistSelfTest"

        private const val CACERT_ASSET_NAME = "cacert.pem"
        private const val SOLOIST_SO = "libsoloist.so"
        private const val SELFTEST_SO = "libgselftest.so"

        private const val CREDENTIALS_DIRNAME = "credentials"
        private const val SETTINGS_DIRNAME = "settings"
        private const val CACHE_DIRNAME = "cache"
        private const val API_KEY_FILENAME = "api_key"
        private const val LEGACY_API_KEY_FILENAME = "soloist.api_key"
        private const val CLIENT_TOKEN_FILENAME = "client_token"
        private const val WS_PORT_FILENAME = "ws.port"
        private const val WS_ADDR_FILENAME = "ws.addr"

        private const val MAX_LOG_LINES = 400
        private const val MAX_SELFTEST_OUTPUT_LINES = 10
        private const val MAX_SELFTEST_OUTPUT_CHARS = 800
        private const val SETUP_API_KEY_TIMEOUT_SEC = 15L
        private const val WS_PORT_POLL_INTERVAL_MS = 100L
        private const val PROBE_MIN_RUN_MS = 1600L
        private const val PROBE_MAX_WAIT_MS = 4000L

        private val HEX_CHARS = "0123456789abcdef".toCharArray()
        private val WS_PORT_REGEX = Regex("""api:\s*websocket server listening on [^:]+:(\d+)""")
        private val selfTestSeq = AtomicInteger(0)

        private val FATAL_EXIT_CODES = setOf(126, 127, 132, 134, 135, 137, 139, 159)
        private val FATAL_OUTPUT_PATTERNS = listOf(
            "SIGSYS",
            "SIGSEGV",
            "SIGABRT",
            "SIGILL",
            "Bad system call",
            "Segmentation fault",
            "error while loading shared libraries",
            "cannot open shared object file",
            "symbol lookup error",
            "relocation error",
            "Exec format error",
        )
    }
}
