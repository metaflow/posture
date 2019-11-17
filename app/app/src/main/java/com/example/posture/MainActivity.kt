package com.example.posture

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.collections.HashSet

private val TAG: String = MainActivity::class.java.simpleName

val serviceUUID = UUID.fromString("6a800001-b5a3-f393-e0a9-e50e24dcca9e")
val characteristicUUID = UUID.fromString("6a806050-b5a3-f393-e0a9-e50e24dcca9e")
val enableNotificationDescriptorUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")


class MainActivity : AppCompatActivity() {

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $requestCode, $permissions, $grantResults")
        startScan()
    }

    class GattCallback(val stateConnected: () -> Unit, val stateDisconnected: () -> Unit) :
        BluetoothGattCallback() {
        private var characteristic: BluetoothGattCharacteristic? = null

        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    Log.i(
                        TAG, "Attempting to start service discovery: " +
                                gatt.discoverServices()
                    )
                    stateConnected()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    gatt.close()
                    stateDisconnected()
                }
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "onServicesDiscovered SUCCESS")
                    gatt.services.forEach { gattService ->
                        Log.i(TAG, "service ${gattService.uuid}")
                        gattService.characteristics.forEach { ch ->
                            Log.i(TAG, "characteristic: ${ch.uuid}")
                            if (ch.uuid == characteristicUUID) {
                                ch.descriptors.forEach { d ->
                                    Log.i(TAG, "descriptor $d ${d.uuid} ${d.value}")
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
                                        value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    }
                                gatt.writeDescriptor(descriptor)
                                ch.descriptors.forEach { d ->
                                    Log.i(TAG, "descriptor $d ${d.uuid} ${d.value}")
                                }
                            }
                        }
                    }
                }
                else -> Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            ch: BluetoothGattCharacteristic?
        ) {
            Log.i(
                TAG,
                "${gatt?.device?.address}: ${ch?.value?.map { b ->
                    String.format("%02X", b)
                }}"
            )
        }
    }

    val scan = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            Log.i(TAG, "onScanFailed $errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) return
//            Log.i(TAG, "onScanResult($callbackType, $result)")
            if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(serviceUUID)) == true) {
                Log.i(TAG, "found IMU sensor")
                connect(result.device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.i(TAG, "onBatchScanResults($results)")
        }

    }

    val activeDevices = HashSet<String>()

    private fun connect(device: BluetoothDevice?) {
        Log.i(TAG, "connecting to $device")
        val address = device?.address
        if (device == null || address == null) return
        if (activeDevices.contains(address)) {
            Log.i(TAG, "device $address already active")
            return
        }
        activeDevices.add(address)
        device.connectGatt(this, false, GattCallback({
            activeDevices.remove(address)
            Log.i(TAG, "$address connected ${activeDevices.size} active devices")
            if (activeDevices.size >= 4) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scan)
            }
        }, {
            Log.i(TAG, "$address disconnected ${activeDevices.size} active devices")
            if (activeDevices.size < 4) {
                startScan()
            }
        }))
    }


    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun startScan() {
        // TODO: check if we are not scanning already
        Log.i(TAG, "startScan")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH not granted")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH),
                1
            )
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "BLUETOOTH_ADMIN not granted")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                1
            )
            return
        }

        bluetoothAdapter?.takeIf { !it.isEnabled }?.apply {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, 1)
        }

        bluetoothAdapter?.takeIf { it.isEnabled }?.apply {
            val s = bluetoothAdapter?.bluetoothLeScanner
            Log.i(TAG, "BLE is enabled, starting scan $s")
            val scanSettings = ScanSettings.Builder()
            scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            scanSettings.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            scanSettings.setReportDelay(0)
            s?.startScan(Vector<ScanFilter>(0), scanSettings.build(), scan)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startScan()


    }
}
