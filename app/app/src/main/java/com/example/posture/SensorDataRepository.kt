package com.example.posture

class SensorDataRepository(private val sensorEntityDao: SensorEntityDao) {
    val all: List<SensorEntity> = sensorEntityDao.allEntries()

    suspend fun insert(e: SensorEntity) {
        sensorEntityDao.insert(e)
    }
}