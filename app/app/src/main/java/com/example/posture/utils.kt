package com.example.posture

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

fun Instant.isoFormat(): String {
    return atZone(TimeZone.getDefault().toZoneId()).format(DateTimeFormatter.ofPattern("d HH:mm:ss"))
}
