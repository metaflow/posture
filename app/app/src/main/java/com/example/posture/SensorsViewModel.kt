package com.example.posture

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

private val TAG: String = SensorsViewModel::class.java.simpleName

class SensorsViewModel(application: Application): AndroidViewModel(application) {
    private val repository: SensorDataRepository

    val allSensors = MutableLiveData<TreeMap<String, SensorMeasurement>>()

    init {
        val dao = AppDatabase.getDatabase(application).sensors()
        repository = SensorDataRepository(dao)
    }

    fun insert(e: SensorMeasurement) = viewModelScope.launch {
        repository.insert(e)
        var m = allSensors.value
        if (m == null) m = TreeMap()
        m[e.sensorId] = e
        allSensors.postValue(m)
        withContext(Dispatchers.IO) {
            val all = repository.getAll()
            Log.i(TAG, "${all.size}")
        }
    }
}