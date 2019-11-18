package com.example.posture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SensorEntityDao {
    @Query("SELECT * from sensor")
    fun allEntries(): List<SensorEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: SensorEntity)
}