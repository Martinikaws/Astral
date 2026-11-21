package com.music.spotui.connect.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import java.security.SecureRandom

@SuppressLint("MissingPermission") // The caller checks advertise and connect permissions.
internal class JamAdvertisement(
    private val context: Context,
    private val manager: BluetoothManager,
    private val advertiser: BluetoothLeAdvertiser,
    private val invitation: JamInvitation,
) {
    private var server: BluetoothGattServer? = null
    private var advertisingCallback: AdvertiseCallback? = null

    fun start(onFailure: () -> Unit) {
        val inline = NearbyJamPayload.encodeInline(invitation)
        if (inline != null) {
            broadcast(inline, connectable = false, onFailure = onFailure)
            return
        }
        val id = SecureRandom().nextLong()
        val record = NearbyJamPayload.encodeRead(id, invitation) ?: return onFailure()
        val callback = serverCallback(record) { success ->
            if (server != null) {
                if (success) broadcast(NearbyJamPayload.encodeReference(id), connectable = true, onFailure = onFailure)
                else onFailure()
            }
        }
        val gatt = bluetoothCall { manager.openGattServer(context, callback) } ?: return onFailure()
        server = gatt
        val service = BluetoothGattService(jamService.uuid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                jamCharacteristic, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ,
            ),
        )
        if (bluetoothCall { gatt.addService(service) } != true) onFailure()
    }

    private fun broadcast(payload: ByteArray, connectable: Boolean, onFailure: () -> Unit) {
        val callback = object : AdvertiseCallback() {
            override fun onStartFailure(errorCode: Int) {
                nearbyMainHandler.post { if (advertisingCallback == this) onFailure() }
            }
        }
        advertisingCallback = callback
        val settings = AdvertiseSettings.Builder()
            .setConnectable(connectable)
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .build()
        val primary = AdvertiseData.Builder().addServiceUuid(jamService).build()
        val response = AdvertiseData.Builder().addServiceData(jamService, payload).build()
        if (bluetoothCall { advertiser.startAdvertising(settings, primary, response, callback) } == null) onFailure()
    }

    private fun serverCallback(record: ByteArray, onServiceAdded: (Boolean) -> Unit) =
        object : BluetoothGattServerCallback() {
            private val mtuByDevice = mutableMapOf<BluetoothDevice, Int>()

            override fun onServiceAdded(status: Int, service: BluetoothGattService) {
                nearbyMainHandler.post { onServiceAdded(status == BluetoothGatt.GATT_SUCCESS) }
            }

            override fun onMtuChanged(device: BluetoothDevice, mtu: Int) {
                nearbyMainHandler.post { if (server != null) mtuByDevice[device] = mtu }
            }

            override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    nearbyMainHandler.post { mtuByDevice.remove(device) }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic,
            ) {
                nearbyMainHandler.post {
                    val gatt = server ?: return@post
                    bluetoothCall {
                        when {
                            characteristic.uuid != jamCharacteristic ->
                                gatt.sendResponse(device, requestId, BluetoothGatt.GATT_READ_NOT_PERMITTED, offset, null)
                            offset !in 0..record.size ->
                                gatt.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
                            else -> {
                                val end = minOf(record.size, offset + (mtuByDevice[device] ?: 23) - 1)
                                gatt.sendResponse(
                                    device, requestId, BluetoothGatt.GATT_SUCCESS, offset, record.copyOfRange(offset, end),
                                )
                            }
                        }
                    }
                }
            }
        }

    fun stop() {
        val callback = advertisingCallback
        advertisingCallback = null
        if (callback != null) bluetoothCall { advertiser.stopAdvertising(callback) }
        val gatt = server
        server = null
        bluetoothCall { gatt?.close() }
    }
}

@SuppressLint("MissingPermission") // The caller checks scan and connect permissions.
internal class JamRead(
    private val context: Context,
    private val device: BluetoothDevice,
    private val id: Long,
) {
    private var connection: BluetoothGatt? = null
    private var result: ((JamInvitation?) -> Unit)? = null
    private val timeout = Runnable { complete(null) }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            nearbyMainHandler.post {
                if (connection != gatt) return@post
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    complete(null)
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (bluetoothCall { gatt.requestMtu(NearbyJamPayload.MAX_READ_BYTES + 1) } != true) discover(gatt)
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            nearbyMainHandler.post { if (connection == gatt) discover(gatt) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            nearbyMainHandler.post {
                if (connection != gatt) return@post
                val characteristic = gatt.getService(jamService.uuid)?.getCharacteristic(jamCharacteristic)
                if (status != BluetoothGatt.GATT_SUCCESS || characteristic == null ||
                    bluetoothCall { gatt.readCharacteristic(characteristic) } != true
                ) complete(null)
            }
        }

        @Deprecated("Used for characteristic reads on Android 12 and earlier")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            onCharacteristicRead(gatt, characteristic, characteristic.value?.copyOf() ?: byteArrayOf(), status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            nearbyMainHandler.post {
                if (connection != gatt || characteristic.uuid != jamCharacteristic) return@post
                complete(if (status == BluetoothGatt.GATT_SUCCESS) NearbyJamPayload.decodeRead(id, value) else null)
            }
        }
    }

    fun start(onResult: (JamInvitation?) -> Unit) {
        result = onResult
        connection = bluetoothCall { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) }
        if (connection == null) complete(null) else nearbyMainHandler.postDelayed(timeout, 8_000L)
    }

    private fun discover(gatt: BluetoothGatt) {
        if (bluetoothCall { gatt.discoverServices() } != true) complete(null)
    }

    private fun complete(invitation: JamInvitation?) {
        val onResult = result
        close()
        onResult?.invoke(invitation)
    }

    fun close() {
        result = null
        nearbyMainHandler.removeCallbacks(timeout)
        val gatt = connection
        connection = null
        bluetoothCall { gatt?.close() }
    }
}
