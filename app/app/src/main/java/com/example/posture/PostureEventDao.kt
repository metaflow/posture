package com.example.posture

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PostureEventDao {
    @Query("SELECT * from event")
    suspend fun allEntries(): List<PostureEvent>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(e: PostureEvent): Long
}