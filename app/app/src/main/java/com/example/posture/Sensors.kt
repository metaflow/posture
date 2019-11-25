package com.example.posture

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.ParcelUuid
import android.util.Log
import java.lang.ref.WeakReference
import java.util.*

private val TAG: String = Sensors::class.java.simpleName

interface SensorsObserver {
    fun onMeasurement(measurement: SensorMeasurement) {}
    fun onScanStatus(on: Boolean, aggressive: Boolean) {}
    fun onDisconnected(address: String) {}
}

class Sensors private constructor() {
    val observers = LinkedList<WeakReference<SensorsObserver>>()
    private val activeDevices = HashMap<String, GattCallback>()
    private var scanning = false
    private var aggressiveScan = false

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
        o.onScanStatus(scanning, aggressiveScan)
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

    fun startScan(aggressive: Boolean) {
        Log.i(TAG, "startScan")
        scanning = false
        bluetoothAdapter.takeIf { it.isEnabled }?.apply {
            val s = bluetoothAdapter.bluetoothLeScanner
            s.stopScan(scan)
            Log.i(TAG, "BLE is enabled, starting scan $s aggressive=$aggressive")
            val scanSettings = ScanSettings.Builder()
            scanSettings.setScanMode(if (aggressive) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_LOW_POWER)
            scanSettings.setMatchMode(ScanSettings.MATCH_MODE_STICKY)
            scanSettings.setReportDelay(0)
            s?.startScan(Vector<ScanFilter>(0), scanSettings.build(), scan)
            scanning = true
            aggressiveScan = aggressive
            if (aggressive) {
                Handler().postDelayed({ startScan(false) }, 10_000)
            }
        }
        notifyScanStatus()
    }

    private fun notifyScanStatus() {
        observers.forEach { o -> o.get()?.onScanStatus(scanning, aggressiveScan) }
    }

    private fun connect(device: BluetoothDevice?) {
        Log.i(TAG, "connecting to $device")
        val address = device?.address
        if (device == null || address == null) return
        if (activeDevices.contains(address)) {
            Log.i(TAG, "device $address already active")
            return
        }
        val gattCallback = GattCallback(stateConnected = {
            Log.i(TAG, "$address connected, ${activeDevices.size} active devices")
            if (activeDevices.size >= 4) {
                stopScan()
            }
        }, stateDisconnected = { add: String ->
            Log.i(TAG, "$add, disconnected ${activeDevices.size} active devices")
            activeDevices.remove(add)
            observers.forEach { o -> o.get()?.onDisconnected(add) }
        }, onValue = { add, value ->
            observers.forEach { o -> o.get()?.onMeasurement(value) }
        })
        activeDevices[address] = gattCallback
        device.connectGatt(context, false, gattCallback)
    }

    fun stopScan() {
        bluetoothAdapter.bluetoothLeScanner?.stopScan(scan)
        scanning = false
        notifyScanStatus()
    }

    fun disconnect() {
        stopScan()
        activeDevices.forEach { t, u -> u.disconnect() }
    }
}