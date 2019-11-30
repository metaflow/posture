package com.example.posture

import java.lang.ref.WeakReference
import java.time.Instant
import java.util.*

private data class StatusMessage(val time: Instant, val message: String)

interface MediatorObserver : SensorsObserver {
    fun onUserToggleApp(on: Boolean) {}
    fun onUserToggleNotifications(value: Boolean) {}
    fun onPostureEvent(e: PostureEvent) {}
    fun onStatusMessage(s: String, t: Instant) {}
}

class Mediator private constructor() : SensorsObserver {
    companion object {
        @Volatile
        private var INSTANCE: Mediator? = null

        fun getInstance(): Mediator {
            val tempInstance = INSTANCE
            if (tempInstance != null) {
                return tempInstance
            }
            synchronized(this) {
                val instance = Mediator()
                INSTANCE = instance
                return instance
            }
        }
    }

    private val latestMessages = LinkedList<StatusMessage>()
    private val observations = TreeMap<String, SensorMeasurement>()
    private val observers = LinkedList<WeakReference<MediatorObserver>>()

    fun addObserver(o: MediatorObserver) {
        observers.push(WeakReference(o))
        o.onUserToggleApp(appEnabled)
        o.onUserToggleNotifications(observeNotifications)
        latestMessages.forEach { s -> o.onStatusMessage(s.message, s.time) }
        observations.forEach { (_, u) -> o.onMeasurement(u) }
    }

    var appEnabled = true
        set(value) {
            field = value
            observers.forEach { o -> o.get()?.onUserToggleApp(value) }
        }

    var observeNotifications = false
        set(value) {
            field = value
            observers.forEach { o -> o.get()?.onUserToggleNotifications(value) }
        }

    fun addEvent(e: PostureEvent) {
        observers.forEach { o -> o.get()?.onPostureEvent(e) }
    }

    override fun onMeasurement(measurement: SensorMeasurement) {
        observers.forEach { o -> o.get()?.onMeasurement(measurement) }
        observations[measurement.sensorId] = measurement
    }

    override fun onScanStatus(on: Boolean, aggressive: Boolean) {
        observers.forEach { o -> o.get()?.onScanStatus(on, aggressive) }
    }

    override fun onDisconnected(address: String) {
        observers.forEach { o -> o.get()?.onDisconnected(address) }
    }

    fun addStatusMessage(s: String) {
        val now = Instant.now()
        latestMessages.addLast(StatusMessage(now, s))
        while (latestMessages.size >= 100) latestMessages.removeFirst()
        observers.forEach { o -> o.get()?.onStatusMessage(s, now) }
    }
}