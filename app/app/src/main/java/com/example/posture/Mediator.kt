package com.example.posture

import java.lang.ref.WeakReference
import java.util.*

interface MediatorObserver : SensorsObserver {
    fun onUserToggleApp(on: Boolean) {}
    fun onUserToggleNotifications(value: Boolean) {}
    fun onPostureEvent(e: PostureEvent) {}
    fun onStatusMessage(s: String) {}
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

    val latestMessages = LinkedList<String>()
    val observations = TreeMap<String, SensorMeasurement>()
    val observers = LinkedList<WeakReference<MediatorObserver>>()

    fun addObserver(o: MediatorObserver) {
        observers.push(WeakReference(o))
        o.onUserToggleApp(appEnabled)
        o.onUserToggleNotifications(observeNotifications)
        latestMessages.forEach { s -> o.onStatusMessage(s) }
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
        latestMessages.push(s)
        while (latestMessages.size >= 100) latestMessages.removeFirst()
        observers.forEach { o -> o.get()?.onStatusMessage(s) }
    }
}