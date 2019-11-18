package com.example.posture

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.sql.Time
import kotlin.math.sqrt

class XYZ {
    var x: Double = 0.0
    var y: Double = 0.0
    var z: Double = 0.0

    override fun toString(): String {
        return "%.2f %.2f %.2f".format(x, y, z)
    }

    fun normalize() {
        val m = mag
        x /= m
        y /= m
        z /= m
    }

    val mag: Double
        get() {
            return sqrt(x * x + y * y + z * z)
        }
}

class SensorData {
    val Accelaration = XYZ()
    override fun toString(): String {
        return "$Accelaration"
    }
}

@Entity(tableName = "sensor")
data class SensorEntity(
    val sensorId: String,
    val time: Time,
    val ax: Double,
    val ay: Double,
    val az: Double
) {
    @PrimaryKey(autoGenerate = true)
    private val id = 0

}