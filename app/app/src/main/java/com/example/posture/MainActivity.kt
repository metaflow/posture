package com.example.posture

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import java.util.*
import kotlin.collections.HashSet

private val TAG: String = MainActivity::class.java.simpleName

val serviceUUID = UUID.fromString("6a800001-b5a3-f393-e0a9-e50e24dcca9e")
val characteristicUUID = UUID.fromString("6a806050-b5a3-f393-e0a9-e50e24dcca9e")
val enableNotificationDescriptorUUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

class MainActivity : AppCompatActivity() {

    private val CHANNEL_ID = "channel_id_posture"
    private val activeDevices = HashSet<String>()
    private lateinit var sensorsViewModel: SensorsViewModel

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $requestCode, $permissions, $grantResults")
        startScan()
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

    var flipped = false

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
            applicationContext, false,
            GattCallback(stateConnected = {
                activeDevices.add(address)
                Log.i(TAG, "$address connected, ${activeDevices.size} active devices")
                if (activeDevices.size >= 4) {
                    bluetoothAdapter?.bluetoothLeScanner?.stopScan(scan)
                }
            }, stateDisconnected = { add: String ->
                Log.i(TAG, "$add, disconnected ${activeDevices.size} active devices")
                activeDevices.remove(add)
                if (activeDevices.size < 4) {
                    startScan()
                }
            }, onValue = { add, value ->
                Log.v(TAG, "value update $add $value")
                if (value.az < 0 != flipped) {
                    flipped = value.az < 0
                    Log.i(TAG, "flipped $flipped")
                    if (flipped) {
                        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_notification)
                            .setContentTitle("title")
                            .setContentText("text\ncontent")
                            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                            .setTimeoutAfter(5000)
                        val notification = builder.build()
                        with(NotificationManagerCompat.from(this)) {
                            val notificationManager: NotificationManager =
                                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(0, notification)
                        }
                    } else {
                        with(NotificationManagerCompat.from(this)) {
                            val notificationManager: NotificationManager =
                                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.cancel(0)
                        }
                    }
                }
                sensorsViewModel.onMeasurement(value)
            })
        )
    }


    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the systeGm
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
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
        createNotificationChannel()
        setContentView(R.layout.activity_main)
        sensorsViewModel = ViewModelProvider(this).get(SensorsViewModel::class.java)
        sensorsViewModel.allSensors.observe(this, androidx.lifecycle.Observer { sensors ->
            val b = StringBuilder()
            sensors.forEach { (t, u) ->
                b.appendln(
                    "${t.substring(0, 2)} (%+.1f, %+.1f,%+.1f)".format(
                        u.ax,
                        u.ay,
                        u.az
                    )
                )
            }
            findViewById<TextView>(R.id.rawtext).text = b
        })
        startScan()
    }

    fun recordGood() {
        sensorsViewModel.onPostureEvent(PostureEvent(PostureEvent.Type.USER_OBSERVED_HEALTHY.ordinal))
    }

    fun recordBad() {
        sensorsViewModel.onPostureEvent(PostureEvent(PostureEvent.Type.USER_OBSERVED_UNHEALTHY.ordinal))
    }
}
