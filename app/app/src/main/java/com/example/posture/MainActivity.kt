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

private val TAG: String = MainActivity::class.java.simpleName

val uuid = UUID.fromString("6a800001-b5a3-f393-e0a9-e50e24dcca9e")

class MainActivity : AppCompatActivity() {

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $requestCode, $permissions, $grantResults")
        run()
    }

    val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server.")
                    Log.i(TAG, "Attempting to start service discovery: " +
                            gatt.discoverServices())
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server.")
                }
            }
        }

        // New services discovered
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "onServicesDiscovered SUCCESS")
                    gatt.services.forEach { gattService ->
                        Log.i(TAG,"service ${gattService.uuid}")
                        gattService.characteristics.forEach { characteristic ->
                            Log.i(TAG, "characteristic: ${characteristic.uuid}")
                            if (characteristic.uuid == UUID.fromString("6a806050-b5a3-f393-e0a9-e50e24dcca9e")) {
                                characteristic.descriptors.forEach { d ->
                                    Log.i(TAG, "descriptor $d ${d.uuid} ${d.value}")
                                }
                                Log.i(TAG, "found IMU characteristic. Setting notification: " +  gatt.setCharacteristicNotification(characteristic, true))
                                val uuid: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                                val descriptor = characteristic.getDescriptor(uuid).apply {
                                    value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                }
                                gatt.writeDescriptor(descriptor)
                                characteristic.descriptors.forEach { d ->
                                    Log.i(TAG, "descriptor $d ${d.uuid} ${d.value}")
                                }
                            }
                        }
                    }
                }
                else -> Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        // Result of a characteristic read operation
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.i(TAG, "characteristic update ${characteristic.value}")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            Log.i(TAG, "characteristic update ${characteristic?.value?.map { b -> String.format("%02X", b) }}")
        }
    }

    val scan = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            Log.i(TAG, "onScanFailed $errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.i(TAG, "onScanResult($callbackType, $result)")
            if (result == null) return

            if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(uuid)) == true) {
                Log.i(TAG, "found IMU sensor")
                connect(result.device)
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(this)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.i(TAG, "onBatchScanResults($results)")
        }
    }

    private fun connect(device: BluetoothDevice?) {
        Log.i(TAG, "connecting to $device")
        if (device == null) return
        device.connectGatt(this, false, gattCallback)
    }


    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun run() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "ACCESS_FINE_LOCATION not granted")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH not granted")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH),
                1)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "BLUETOOTH_ADMIN not granted")
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.BLUETOOTH_ADMIN),
                1)
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
            scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            scanSettings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            scanSettings.setReportDelay(0)
            s?.startScan(Vector<ScanFilter>(0), scanSettings.build(), scan)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        run()


    }
}
