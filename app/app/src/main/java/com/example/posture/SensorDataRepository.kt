package com.example.posture

class SensorDataRepository(private val sensorEntityDao: SensorEntityDao) {
//    val all: List<SensorMeasurement> = sensorEntityDao.allEntries()

    suspend fun insert(e: SensorMeasurement) {
        sensorEntityDao.insert(e)
    }

    suspend fun getAll(): List<SensorMeasurement> {
        return sensorEntityDao.allEntries()
    }
}