package com.example.posture

class SensorDataRepository(
    private val sensorEntityDao: SensorEntityDao,
    private val postureEventDao: PostureEventDao
) {
//    val all: List<SensorMeasurement> = sensorEntityDao.allEntries()

    suspend fun insertMeasurement(e: SensorMeasurement) {
        sensorEntityDao.insert(e)
    }

    suspend fun getAllMeasurements(): List<SensorMeasurement> {
        return sensorEntityDao.allEntries()
    }

    suspend fun insertEvent(e: PostureEvent): Long {
        return postureEventDao.insert(e)
    }

    suspend fun getAll(): List<PostureEvent> {
        return postureEventDao.allEntries()
    }
}