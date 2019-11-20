package com.example.posture

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import java.lang.ref.WeakReference
import java.util.*

private val TAG: String = Sensors::class.java.simpleName

interface SensorsObserver {
    fun onMeasurement(measurement: SensorMeasurement)
}

class Sensors private constructor() {
    val observers = LinkedList<WeakReference<SensorsObserver>>()
    private val activeDevices = HashSet<String>()

    var context: Context? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
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
    // TODO: make flow interface

    fun addObserver(o: SensorsObserver) {
        observers.push(WeakReference(o))
    }

    companion object {
        @Volatile
        private var INSTANCE: Sensors? = null

        fun getInstance(context: Context): Sensors {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Sensors()
                INSTANCE = instance
                instance.context = context
                val bluetoothManager =
                    context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                instance.bluetoothAdapter = bluetoothManager.adapter
                return instance
            }
        }
    }

    fun startScan() {
        Log.i(TAG, "startScan")
        bluetoothAdapter.takeIf { it.isEnabled }?.apply {
            val s = bluetoothAdapter.bluetoothLeScanner
            Log.i(TAG, "BLE is enabled, starting scan $s")
            val scanSettings = ScanSettings.Builder()
            scanSettings.setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
            scanSettings.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            scanSettings.setReportDelay(0)
            s?.startScan(Vector<ScanFilter>(0), scanSettings.build(), scan)
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
        device.connectGatt(
            context, false,
            GattCallback(stateConnected = {
                activeDevices.add(address)
                Log.i(TAG, "$address connected, ${activeDevices.size} active devices")
                if (activeDevices.size >= 4) {
                    bluetoothAdapter.bluetoothLeScanner?.stopScan(scan)
                }
            }, stateDisconnected = { add: String ->
                Log.i(TAG, "$add, disconnected ${activeDevices.size} active devices")
                activeDevices.remove(add)
                if (activeDevices.size < 4) {
                    startScan()
                }
            }, onValue = { add, value ->
                observers.forEach { o -> o.get()?.onMeasurement(value) }
            })
        )
    }
}