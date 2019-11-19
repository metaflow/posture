package com.example.posture

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI

class QTest {

    @Test
    fun rotate() {
        val q = Q(0.0, 1.0, 0.0, 0.0)
        val w = q.rotate(Q(0.0, 0.0, 0.0, 1.0), PI / 2)
        assertEquals(Q(0.0, 0.0, 1.0, 0.0).toString(), w.toString())
    }
}