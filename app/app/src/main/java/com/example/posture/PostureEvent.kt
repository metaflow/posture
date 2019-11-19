package com.example.posture

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "event")
data class PostureEvent(var type: Int) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    enum class Type {
        USER_OBSERVED_HEALTHY, USER_OBSERVED_UNHEALTHY
    }
}