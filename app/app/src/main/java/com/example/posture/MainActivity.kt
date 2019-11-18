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
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*
import kotlin.collections.HashSet
import kotlin.math.sqrt

private val TAG: String = MainActivity::class.java.simpleName

val serviceUUID = UUID.fromString("6a800001-b5a3-f393-e0a9-e50e24dcca9e")
val characteristicUUID = UUID.fromString("6a806050-b5a3-f393-e0a9-e50e24dcca9e")
val enableNotificationDescriptorUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class MainActivity : AppCompatActivity() {

    private val activeDevices = HashSet<String>()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $requestCode, $permissions, $grantResults")
        startScan()
    }

    class GattCallback(val stateConnected: () -> Unit, val stateDisconnected: (address: String) -> Unit, val onValue: (address: String, data: SensorData) -> Unit) :
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
                    stateDisconnected(gatt.device.address)
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
            val address = gatt?.device?.address
            Log.i(TAG, "$address: ${ch?.value?.map { b -> String.format("%02X", b) }?.joinToString { x -> x }}")
            val data = SensorData()
            val get = { p: Int -> Double
                when {
                    ch?.value == null -> 0
                    p + 1 > ch.value?.size!! -> 0
                    else -> ch.value?.get(p)!! * 256 + ch.value?.get(p + 1)!!
                }
            }
            data.Accelaration.x = get(0).toDouble()
            data.Accelaration.y = get(2).toDouble()
            data.Accelaration.z = get(4).toDouble()
            data.Accelaration.normalize()
            if (address != null) onValue(address, data)
        }
    }

    private val scan = object : ScanCallback() {
        override fun onScanFailed(errorCode: Int) {
            Log.i(TAG, "onScanFailed $errorCode")
        }

        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (result == null) return
            Log.v(TAG, "onScanResult($callbackType, $result)")
            if (result.scanRecord?.serviceUuids?.contains(ParcelUuid(serviceUUID)) == true) {
                Log.i(TAG, "found IMU sensor")
                connect(result.device)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.i(TAG, "onBatchScanResults($results)")
        }

    }

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
            updateSensorDisplay()
        }, { add: String ->
            Log.i(TAG, "$add disconnected ${activeDevices.size} active devices")
            sensorState.remove(add)
            if (activeDevices.size < 4) {
                startScan()
            }
        },  { add, value ->
            Log.v(TAG, "value update $add $value")
            sensorState[address] = value
            updateSensorDisplay()
        }))
    }

    val sensorState = TreeMap<String, SensorData>()


    private fun updateSensorDisplay() {
        this.runOnUiThread {
            val b = StringBuilder()
            sensorState.forEach { (t, u) ->
                b.appendln("${t.substring(0, 2)} $u")
            }
            findViewById<TextView>(R.id.rawtext).text = b
        }
    }


    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    private fun startScan() {
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
