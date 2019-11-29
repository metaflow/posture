package com.example.posture

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

private val TAG: String = MainActivity::class.java.simpleName

val serviceUUID = UUID.fromString("6a800001-b5a3-f393-e0a9-e50e24dcca9e")!!
val characteristicUUID = UUID.fromString("6a806050-b5a3-f393-e0a9-e50e24dcca9e")!!
val enableNotificationDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")!!

class MainActivity : AppCompatActivity(), MediatorObserver {

    private val ENABLE_BLUETOOTH_REQUEST = 0
    val messages = LinkedList<String>()

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.i(TAG, "onRequestPermissionsResult $requestCode, $permissions, $grantResults")
        startPostureService()
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy(LazyThreadSafetyMode.NONE) {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothManager.adapter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Mediator.getInstance().addObserver(this)
        startPostureService()
    }

    val sensors = TreeMap<String, SensorMeasurement>()

    private fun startPostureService() {
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
            startActivityForResult(enableBtIntent, ENABLE_BLUETOOTH_REQUEST)
            return
        }

        Log.i(TAG, "starting foreground service")
        startForegroundService(Intent(applicationContext, PostureService::class.java))
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordGood(view: View) {
        Mediator.getInstance()
            .addEvent(PostureEvent(PostureEvent.Type.USER_OBSERVED_HEALTHY.ordinal))
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordBad(view: View) {
        Mediator.getInstance()
            .addEvent(PostureEvent(PostureEvent.Type.USER_OBSERVED_UNHEALTHY.ordinal))
    }

    @Suppress("UNUSED_PARAMETER")
    fun recordNotSitting(view: View) {
        Mediator.getInstance()
            .addEvent(PostureEvent(PostureEvent.Type.USER_OBSERVED_NOT_SITTING.ordinal))
    }

    @Suppress("UNUSED_PARAMETER")
    fun forceScan(view: View) {
        if (Sensors.getInstance(this).scanning) {
            Sensors.getInstance(this).stopScan()
        } else {
            Sensors.getInstance(this).startScan(true)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun toggleApp(view: View) {
        Mediator.getInstance().appEnabled = !Mediator.getInstance().appEnabled
    }

    @Suppress("UNUSED_PARAMETER")
    fun toggleObserveNotifications(view: View) {
        Mediator.getInstance().observeNotifications = !Mediator.getInstance().observeNotifications
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == ENABLE_BLUETOOTH_REQUEST) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "bluetooth should now be enabled")
                startPostureService()
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onStatusMessage(s: String) = runOnUiThread {
        messages.push(s)
        while (messages.size > 10) messages.removeFirst()
        findViewById<TextView>(R.id.messagesText).text =
            messages.joinToString("\n")
    }

    override fun onMeasurement(measurement: SensorMeasurement) = runOnUiThread {
        sensors[measurement.sensorId] = measurement
        val b = StringBuilder()
        sensors.forEach { (t, u) ->
            b.appendln(
                "${t.substring(0, 2)} (%+.1f, %+.1f, %+.1f)".format(
                    u.ax,
                    u.ay,
                    u.az
                )
            )
        }
        findViewById<TextView>(R.id.rawtext).text = b
    }

    override fun onScanStatus(on: Boolean, aggressive: Boolean) = runOnUiThread {
        findViewById<Button>(R.id.scan).text = if (on) "STOP SCANNING" else "SCAN"
    }

    override fun onUserToggleApp(on: Boolean) = runOnUiThread {
        findViewById<Button>(R.id.onOffButton).text = if (on) "Turn off" else "Turn on"
    }

    override fun onUserToggleNotifications(value: Boolean) = runOnUiThread {
        super.onUserToggleNotifications(value)
        findViewById<Button>(R.id.toggleObserveButton).text =
            if (value) "Stop notifications" else "Start notifications"
    }
}
