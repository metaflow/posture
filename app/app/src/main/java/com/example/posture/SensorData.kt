package com.example.posture

import androidx.room.Entity
import androidx.room.PrimaryKey
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

@Entity(tableName = "sensor")
data class SensorMeasurement(
    val sensorId: String,
    val time: Long,
    var ax: Double,
    var ay: Double,
    var az: Double
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0

    var eventID: Long = 0

    fun normalize() {
        val a = XYZ()
        a.x = ax
        a.y = ay
        a.z = az
        a.normalize()
        ax = a.x
        ay = a.y
        az = a.z
    }
}