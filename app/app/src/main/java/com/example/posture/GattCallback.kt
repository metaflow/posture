package com.example.posture

import android.bluetooth.*
import android.util.Log
import java.time.Instant

private val TAG: String = GattCallback::class.java.simpleName

class GattCallback(
    val stateConnected: () -> Unit,
    val stateDisconnected: (address: String) -> Unit,
    val onValue: (address: String, data: SensorMeasurement) -> Unit
) :
    BluetoothGattCallback() {
    private var characteristic: BluetoothGattCharacteristic? = null
    private var bluetoothGatt: BluetoothGatt? = null

    override fun onConnectionStateChange(
        gatt: BluetoothGatt,
        status: Int,
        newState: Int
    ) {
        bluetoothGatt = gatt
        when (newState) {
            BluetoothProfile.STATE_CONNECTED -> {
                Log.i(
                    TAG,
                    "Connected to GATT server."
                )
                Log.i(
                    TAG,
                    "Attempting to start service discovery: " +
                            gatt.discoverServices()
                )
                stateConnected()
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                gatt.close()
                stateDisconnected(gatt.device.address)
            }
        }
    }

    // New services discovered
    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        when (status) {
            BluetoothGatt.GATT_SUCCESS -> {
                Log.i(
                    TAG,
                    "onServicesDiscovered SUCCESS"
                )
                gatt.services.forEach { gattService ->
                    Log.i(
                        TAG,
                        "service ${gattService.uuid}"
                    )
                    gattService.characteristics.forEach { ch ->
                        Log.i(
                            TAG,
                            "characteristic: ${ch.uuid}"
                        )
                        if (ch.uuid == characteristicUUID) {
                            ch.descriptors.forEach { d ->
                                Log.i(
                                    TAG,
                                    "descriptor $d ${d.uuid} ${d.value}"
                                )
                            }
                            characteristic = ch
                            Log.i(
                                TAG,
                                "found IMU characteristic. Setting notification: " + gatt.setCharacteristicNotification(
                                    ch,
                                    true
                                )
                            )
                            val descriptor =
                                ch.getDescriptor(enableNotificationDescriptorUUID).apply {
                                    value =
                                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }
                            gatt.writeDescriptor(descriptor)
                            ch.descriptors.forEach { d ->
                                Log.i(
                                    TAG,
                                    "descriptor $d ${d.uuid} ${d.value}"
                                )
                            }
                        }
                    }
                }
            }
            else -> Log.w(
                TAG,
                "onServicesDiscovered received: $status"
            )
        }
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        ch: BluetoothGattCharacteristic?
    ) {
        val address = gatt?.device?.address
        if (address == null) {
            Log.w(
                TAG,
                "no address for device ${gatt?.device}"
            )
            return
        }
        Log.i(
            TAG,
            "$address: ${ch?.value?.map { b -> String.format("%02X", b) }?.joinToString { x -> x }}"
        )
        val get = { p: Int ->
            Long
            when {
                ch?.value == null -> 0
                p + 1 > ch.value?.size!! -> 0
                else -> ch.value?.get(p)!! * 256 + ch.value?.get(p + 1)!!
            }
        }
        val x = get(0).toLong()
        val y = get(2).toLong()
        val z = get(4).toLong()
        if (x * x + y * y + z * z > 0) {
            val data = SensorMeasurement(
                address,
                Instant.now().toEpochMilli(),
                x,
                y,
                z
            )
            data.normalize()
            onValue(address, data)
        } else {
            Log.i(TAG, "invalid sensor data, trying to reconnect")
            disconnect()
        }
    }

    fun disconnect() {
        val descriptor =
            characteristic?.getDescriptor(enableNotificationDescriptorUUID).apply {
                this?.value ?: BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            }
        bluetoothGatt?.writeDescriptor(descriptor)
        bluetoothGatt?.disconnect()
    }
}