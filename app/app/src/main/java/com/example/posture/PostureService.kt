package com.example.posture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModelProvider
import kotlin.random.Random

private val TAG: String = PostureService::class.java.simpleName


class PostureService : Service(), SensorsObserver {

    private val backgroundChannelID = "channel_id_posture_bg"
    private val messagesChannelID = "channel_id_posture_fg"
    var flipped = false
    var started = false
    var observeNotifications = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
            .create(SensorsViewModel::class.java)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand $started $startId $flags")
        if (intent?.hasExtra("ObserverNotifications") == true) {
            observeNotifications = intent.getBooleanExtra("ObserverNotifications", false)
            Log.i(TAG, "observe notifications = $observeNotifications")

            sheduleObserveNotification()
        }
        if (started) return START_STICKY
        started = true
        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, 0
        )

        createNotificationChannel()
        val notification = NotificationCompat.Builder(
            this,
            backgroundChannelID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("posture")
            .setContentText("onStartCommand")
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        Sensors.getInstance(this).context = this
        Sensors.getInstance(this).startScan(false)
        Sensors.getInstance(this).addObserver(this)
        return START_STICKY
    }

    private fun showObserveNotification() {
        if (!observeNotifications) return
        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, 0
        )
        val builder = NotificationCompat.Builder(this, messagesChannelID)
            .setSmallIcon(R.drawable.ic_fix_posture)
            .setContentTitle("observe")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setTimeoutAfter(5000)
        val notification = builder.build()
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(3, notification)
        sheduleObserveNotification()
    }

    private fun sheduleObserveNotification() {
        if (!observeNotifications) return
        val delay = getNotificationDelay()
        Log.i(TAG, "next notification in $delay ms")
        Handler().postDelayed({ showObserveNotification() }, delay)
    }

    private fun getNotificationDelay(): Long {
        return Random.nextLong(5, 30) * 60_000
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var name = getString(R.string.channel_name)
            var descriptionText = getString(R.string.channel_description)
            var importance = NotificationManager.IMPORTANCE_DEFAULT
            var channel = NotificationChannel(backgroundChannelID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            name = getString(R.string.channel_name)
            descriptionText = getString(R.string.channel_description)
            importance = NotificationManager.IMPORTANCE_DEFAULT
            channel = NotificationChannel(messagesChannelID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onMeasurement(measurement: SensorMeasurement) {
        @Suppress("SimplifyBooleanWithConstants")
        if (measurement.az < 0 != flipped && false) {
            flipped = measurement.az < 0
            Log.i(TAG, "flipped $flipped")
            if (flipped) {
                val builder = NotificationCompat.Builder(this, backgroundChannelID)
                    .setSmallIcon(R.drawable.ic_fix_posture)
                    .setContentTitle("FLIPPED")
                    .setContentText("sensor is flipped")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setTimeoutAfter(5000)
                val notification = builder.build()

                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(2, notification)
            } else {
                val notificationManager: NotificationManager =
                    getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(2)
            }
        }
    }

    override fun onScanStatus(on: Boolean, aggressive: Boolean) {
    }

    override fun onDisconnected(address: String) {
    }
}
