package com.example.posture

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.collections.ArrayList
import kotlin.concurrent.withLock

private val TAG: String = SensorsViewModel::class.java.simpleName

class SensorsViewModel(application: Application): AndroidViewModel(application) {
    private val repository: SensorDataRepository

    val allSensors = MutableLiveData<TreeMap<String, SensorMeasurement>>()
    private val queue: LinkedList<SensorMeasurement> = LinkedList()
    private var queueLock = ReentrantLock()

    init {
        val db = AppDatabase.getDatabase(application)
        repository = SensorDataRepository(db.sensors(), db.events())
    }

    fun onMeasurement(e: SensorMeasurement) = viewModelScope.launch {
        queueLock.withLock {
            queue.addLast(e)
            removeOld()
        }
        var m = allSensors.value
        if (m == null) m = TreeMap()
        m[e.sensorId] = e
        allSensors.postValue(m)
    }

    private fun removeOld() {
        queueLock.withLock {
            val t = Instant.now().toEpochMilli() - 10_000
            while (!queue.isEmpty() && queue.peekFirst()!!.time < t) queue.removeFirst()
        }
    }

    fun onPostureEvent(e: PostureEvent) = viewModelScope.launch {
        val id = repository.insertEvent(e)
        removeOld()
        queueLock.lock()
        val c = ArrayList<SensorMeasurement>(queue)
        queueLock.unlock()
        c.forEach { m ->
            m.eventID = id
            repository.insertMeasurement(m)
        }
        Log.i(TAG, "added new event $id $e with ${queue.size} measurements attached")
    }
}