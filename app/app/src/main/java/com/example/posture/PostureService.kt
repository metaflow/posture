package com.example.posture

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

private val TAG: String = PostureService::class.java.simpleName


class PostureService : Service(), SensorsObserver {

    private val CHANNEL_ID = "channel_id_posture"
    var flipped = false

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand")
        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            notificationIntent, 0
        )


        createNotificationChannel()
        val notification = NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("posture")
            .setContentText("onStartCommand")
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)
        Sensors.getInstance(this).context = this
        Sensors.getInstance(this).startScan()
        Sensors.getInstance(this).addObserver(this)
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
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

    override fun onMeasurement(measurement: SensorMeasurement) {
        if (measurement.az < 0 != flipped) {
            flipped = measurement.az < 0
            Log.i(TAG, "flipped $flipped")
            if (flipped) {
                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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
}
