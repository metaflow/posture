package com.example.posture

import java.lang.ref.WeakReference
import java.util.*

interface MediatorObserver {
    fun onUserToggleApp(on: Boolean) {}
    fun onUserToggleNotifications(value: Boolean) {}
}

class Mediator private constructor() {
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

    val observers = LinkedList<WeakReference<MediatorObserver>>()

    fun addObserver(o: MediatorObserver) {
        observers.push(WeakReference(o))
        o.onUserToggleApp(appEnabled)
        o.onUserToggleNotifications(observeNotifications)
    }

    var appEnabled = true
        get() = field
        set(value) {
            field = value
            observers.forEach { o -> o.get()?.onUserToggleApp(value) }
        }

    var observeNotifications = false
        get() = field
        set(value) {
            field = value
            observers.forEach { o -> o.get()?.onUserToggleNotifications(value) }
        }
}