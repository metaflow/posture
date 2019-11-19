package com.example.posture

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Q(var w: Double, var x: Double, var y: Double, var z: Double) {

    override fun toString(): String {
        return "%.2f %.2f %.2f %.2f".format(w, x, y, z)
    }

    fun normalize(): Q {
        val m = mag
        return Q(w / m, x / m, y / m, z / m)
    }

    val mag: Double
        get() {
            return sqrt(w * w + x * x + y * y + z * z)
        }

    fun rotate(axis: Q, phi: Double): Q {
        val p = phi / 2
        val t = sin(p)
        return (Q(cos(p), t * axis.x, t * axis.y, t * axis.z) * this) *
                Q(cos(-p), -t * axis.x, -t * axis.y, -t * axis.z)
    }
}

operator fun Q.times(b: Q): Q {
    return Q(
        -x * b.x - y * b.y - z * b.z + b.w * w,
        w * b.x - z * b.y + y * b.z + x * b.w,
        z * b.x + w * b.y - x * b.z + y * b.w,
        -y * b.x + x * b.y + w * b.z + z * b.w
    )
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
        val a = Q(0.0, ax, ay, az).normalize()
        ax = a.x
        ay = a.y
        az = a.z
    }
}