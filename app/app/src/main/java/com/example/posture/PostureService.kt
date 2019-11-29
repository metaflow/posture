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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.concurrent.withLock
import kotlin.random.Random

private val TAG: String = PostureService::class.java.simpleName


class PostureService : Service(), MediatorObserver {
    private var repository: SensorDataRepository? = null
    private val observeNotificationID = 2
    private val backgroundChannelID = "channel_id_posture_bg"
    private val messagesChannelID = "channel_id_posture_fg"
    var started = false
    var nextObserve: Instant? = null
        set(value) {
            field = value
            Mediator.getInstance().addStatusMessage("next observe notification $value")
        }
    var nextRecord: Instant? = null
        set(value) {
            field = value
            Mediator.getInstance().addStatusMessage("next record $value")
        }
    private val queue: LinkedList<SensorMeasurement> = LinkedList()
    private var queueLock = ReentrantLock()

    companion object {
        @Volatile
        private var INSTANCE: PostureService? = null

        fun getInstance(): PostureService? {
            return INSTANCE
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.i(TAG, "onStartCommand $started $startId $flags")
        if (started) return START_STICKY

        val db = AppDatabase.getDatabase(this)
        repository = SensorDataRepository(db.sensors(), db.events())
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
        Mediator.getInstance().addObserver(this)
        Sensors.getInstance(this).addObserver(Mediator.getInstance())
        scheduleRecord()
        return START_STICKY
    }

    private fun showObserveNotification() {
        nextObserve = null
        if (!Mediator.getInstance().observeNotifications) return
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
            .setTimeoutAfter(60_000)
        val notification = builder.build()
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(observeNotificationID, notification)
        scheduleObserveNotification()
    }

    private fun hideObserveNotification() {
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(observeNotificationID)
    }

    private fun scheduleObserveNotification() {
        if (!Mediator.getInstance().observeNotifications) {
            nextObserve = null
            return
        }
        val delay = Random.nextLong(5 * 60_000, 30 * 60_000)
        Log.i(TAG, "next notification in $delay ms")
        nextObserve = Instant.now().plusMillis(delay)
        Handler().postDelayed({ showObserveNotification() }, delay)
    }

    private fun scheduleRecord() {
        if (nextRecord != null) return
        val delay = Random.nextLong(2 * 60_000, 10 * 60_000)
        Log.i(TAG, "next record in $delay ms")
        nextRecord = Instant.now().plusMillis(delay)
        Handler().postDelayed({ recordSensors() }, delay)
    }

    private fun recordSensors() {
        nextRecord = null
        Mediator.getInstance().addEvent(PostureEvent(PostureEvent.Type.ARCHIVE.ordinal))
        scheduleRecord()
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

    override fun onUserToggleApp(on: Boolean) {
        super.onUserToggleApp(on)
        if (on) {
            Sensors.getInstance(this).startScan(true)
        } else {
            Sensors.getInstance(this).disconnect()
        }
    }

    override fun onUserToggleNotifications(value: Boolean) {
        super.onUserToggleNotifications(value)
        if (value) {
            scheduleObserveNotification()
        }
    }

    override fun onPostureEvent(e: PostureEvent) {
        GlobalScope.launch(Dispatchers.IO) {
            if (repository != null) {
                val id = repository!!.insertEvent(e)
                removeOld()
                queueLock.lock()
                val c = ArrayList<SensorMeasurement>(queue)
                queueLock.unlock()
                c.forEach { m ->
                    m.eventID = id
                    repository!!.insertMeasurement(m)
                }
                Mediator.getInstance().addStatusMessage("$id ${e.type} with ${queue.size} points")
                Log.i(TAG, "added new event $id $e with ${queue.size} measurements attached")
            }
        }
    }

    override fun onMeasurement(measurement: SensorMeasurement) {
        queueLock.withLock {
            queue.addLast(measurement)
            removeOld()
        }
    }

    override fun onScanStatus(on: Boolean, aggressive: Boolean) {
        Mediator.getInstance().addStatusMessage("scanning $on aggressive $aggressive")
    }

    override fun onDisconnected(address: String) {
    }

    private fun removeOld() {
        queueLock.withLock {
            val t = Instant.now().toEpochMilli() - 10_000
            while (!queue.isEmpty() && queue.peekFirst()!!.time < t) queue.removeFirst()
        }
    }
}
