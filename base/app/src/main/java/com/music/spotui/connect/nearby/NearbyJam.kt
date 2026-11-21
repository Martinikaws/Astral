package com.music.spotui.connect.nearby

import android.Manifest.permission
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import app.myspoti.host.android.MyspotiRuntime
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class NearbyHost(val name: String?, val token: String)

internal val jamService = ParcelUuid(UUID.fromString("153a8725-aee0-4b6c-9dde-7caea612f282"))
internal val jamCharacteristic: UUID = UUID.fromString("782784d6-c67b-4bb6-8a79-111ad5aadbef")
internal val nearbyMainHandler = Handler(Looper.getMainLooper())

internal fun <T> bluetoothCall(block: () -> T): T? = try {
    block()
} catch (_: SecurityException) {
    null
} catch (_: IllegalStateException) {
    null
}

internal fun nearbyPermissions(host: Boolean): Array<String> = when {
    Build.VERSION.SDK_INT >= 31 -> arrayOf(
        permission.BLUETOOTH_CONNECT,
        if (host) permission.BLUETOOTH_ADVERTISE else permission.BLUETOOTH_SCAN,
    )
    host -> emptyArray()
    else -> arrayOf(permission.ACCESS_FINE_LOCATION)
}

internal fun Context.hasNearbyPermissions(host: Boolean): Boolean =
    nearbyPermissions(host).all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/** Call on the main thread. The application context is supplied by the nearby Jam composables. */
@MainThread
@SuppressLint("MissingPermission") // Each role checks its runtime permissions before using Bluetooth.
object NearbyJam {
    private const val HOST_EXPIRY_MS = 12_000L
    private const val MAX_HOSTS = 16

    private var context: Context? = null
    private val manager get() = context?.getSystemService(BluetoothManager::class.java)
    internal val supported get() =
        manager?.adapter != null && context?.packageManager?.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) == true
    internal val bluetoothEnabled = MutableStateFlow(false)
    internal val advertising = MutableStateFlow(false)
    internal val advertiseFailed = MutableStateFlow(false)
    internal val scanFailed = MutableStateFlow(false)

    private val nearbyHosts = MutableStateFlow<List<NearbyHost>>(emptyList())
    val found: StateFlow<List<NearbyHost>> = nearbyHosts
    private var outgoing: JamAdvertisement? = null
    private var advertisedSession: String? = null
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var read: JamRead? = null
    private data class SeenHost(val host: NearbyHost, var lastSeen: Long)
    private val sightings = LinkedHashMap<NearbyJamPayload.Advertisement, SeenHost>()
    private val expireSightings = object : Runnable {
        override fun run() {
            if (scanCallback == null) return
            val cutoff = SystemClock.elapsedRealtime() - HOST_EXPIRY_MS
            if (sightings.values.removeAll { it.lastSeen < cutoff }) publishSightings()
            nearbyMainHandler.postDelayed(this, 1_000L)
        }
    }

    internal fun initialize(context: Context) {
        if (this.context != null || !MyspotiRuntime.isStarted) return
        val app = context.applicationContext
        this.context = app
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = refreshBluetooth()
            },
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        refreshBluetooth()
        MyspotiRuntime.scope.launch {
            MyspotiRuntime.api.jam.session.collect { session ->
                if (session != null) stopScanning()
                if (advertisedSession != null && (session == null || session.id != advertisedSession || !session.isHost)) {
                    stopAdvertising()
                }
            }
        }
    }

    internal fun refreshBluetooth() {
        bluetoothEnabled.value = bluetoothCall { manager?.adapter?.isEnabled == true } == true
        if (!bluetoothEnabled.value) {
            stopAdvertising()
            stopScanning()
        }
    }

    fun advertise(inviteLink: String) {
        val app = context ?: return
        val bluetooth = manager ?: return
        if (!app.hasNearbyPermissions(host = true)) return
        val session = MyspotiRuntime.api.jam.session.value?.takeIf { it.isHost } ?: return
        // The API may give the caller an opaque short link. Its token is already in the host session.
        val currentToken = session.joinToken?.let(NearbyJamPayload::token)
        val linkToken = NearbyJamPayload.token(inviteLink)
        val token = currentToken ?: linkToken
        stopAdvertising()
        if (token == null || (linkToken != null && currentToken != null && linkToken != currentToken)) {
            advertiseFailed.value = true
            return
        }
        val advertiser = bluetoothCall { bluetooth.adapter?.bluetoothLeAdvertiser }
        if (advertiser == null) {
            advertiseFailed.value = true
            return
        }
        val invitation = JamInvitation(token, session.members.firstOrNull { it.host }?.name)
        val adapter = JamAdvertisement(app, bluetooth, advertiser, invitation)
        outgoing = adapter
        advertisedSession = session.id
        advertising.value = true
        adapter.start {
            if (outgoing == adapter) {
                stopAdvertising()
                advertiseFailed.value = true
            }
        }
    }

    fun stopAdvertising() {
        val previous = outgoing
        outgoing = null
        advertisedSession = null
        advertising.value = false
        advertiseFailed.value = false
        previous?.stop()
    }

    fun startScanning() {
        val app = context ?: return
        if (scanCallback != null || !app.hasNearbyPermissions(host = false)) return
        if (MyspotiRuntime.api.jam.session.value != null) return
        val leScanner = bluetoothCall { manager?.adapter?.bluetoothLeScanner } ?: return
        scanFailed.value = false
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                nearbyMainHandler.post {
                    if (scanCallback == this) processResult(app, result)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                nearbyMainHandler.post {
                    if (scanCallback == this) reportScanFailure()
                }
            }
        }
        scanner = leScanner
        scanCallback = callback
        val filters = listOf(ScanFilter.Builder().setServiceUuid(jamService).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_BALANCED).build()
        if (bluetoothCall { leScanner.startScan(filters, settings, callback) } == null) {
            reportScanFailure()
        } else {
            nearbyMainHandler.post(expireSightings)
        }
    }

    fun stopScanning() {
        val callback = scanCallback
        scanCallback = null
        if (callback != null) bluetoothCall { scanner?.stopScan(callback) }
        scanner = null
        read?.close()
        read = null
        nearbyMainHandler.removeCallbacks(expireSightings)
        sightings.clear()
        publishSightings()
        scanFailed.value = false
    }

    private fun reportScanFailure() {
        stopScanning()
        scanFailed.value = true
    }

    private fun processResult(app: Context, result: ScanResult) {
        val bytes = result.scanRecord?.getServiceData(jamService) ?: return
        val advertisement = NearbyJamPayload.decodeAdvertisement(bytes) ?: return
        sightings[advertisement]?.let {
            it.lastSeen = SystemClock.elapsedRealtime()
            return
        }
        when (advertisement) {
            is NearbyJamPayload.Advertisement.Inline -> rememberHost(advertisement, advertisement.invitation)
            is NearbyJamPayload.Advertisement.Reference -> {
                if (read != null) return
                val connection = JamRead(app, result.device, advertisement.id)
                read = connection
                connection.start { invitation ->
                    if (read == connection) {
                        read = null
                        if (invitation != null) rememberHost(advertisement, invitation)
                    }
                }
            }
        }
    }

    private fun rememberHost(advertisement: NearbyJamPayload.Advertisement, invitation: JamInvitation) {
        if (sightings.size >= MAX_HOSTS) sightings.remove(sightings.minBy { it.value.lastSeen }.key)
        sightings[advertisement] = SeenHost(
            NearbyHost(invitation.hostName, invitation.token), SystemClock.elapsedRealtime(),
        )
        publishSightings()
    }

    private fun publishSightings() {
        nearbyHosts.value = sightings.values.map { it.host }.distinctBy { it.token }
    }
}
