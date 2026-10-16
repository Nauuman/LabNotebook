package com.nauuman.measure

import com.nauuman.measure.ar.Geometry
import com.nauuman.measure.ar.Vec3
import com.nauuman.measure.data.Units
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MeasureMathTest {
    @Before
    fun setLocale() {
        Locale.setDefault(Locale.US)
    }

    @Test
    fun polygonAreaOfTiltedSquare() {
        // A 2 m x 0.5 m rectangle standing on a wall (in the XY plane).
        val pts = listOf(Vec3(0f, 0f, 1f), Vec3(2f, 0f, 1f), Vec3(2f, 0.5f, 1f), Vec3(0f, 0.5f, 1f))
        assertEquals(1f, Geometry.polygonArea(pts), 1e-5f)
        assertEquals(5f, Geometry.pathLength(pts, closed = true), 1e-5f)
        assertEquals(4.5f, Geometry.pathLength(pts, closed = false), 1e-5f)
    }

    @Test
    fun metricLengths() {
        assertEquals("4.2 cm", Units.length(0.042f, imperial = false))
        assertEquals("42 cm", Units.length(0.42f, imperial = false))
        assertEquals("1.23 m", Units.length(1.234f, imperial = false))
    }

    @Test
    fun imperialLengths() {
        assertEquals("5½″", Units.length(0.1397f, imperial = true)) // 5.5 in
        assertEquals("3′ 4″", Units.length(1.016f, imperial = true)) // 40 in
        assertEquals("2′ 0″", Units.length(0.6083f, imperial = true)) // 23.95 in rounds up
    }

    @Test
    fun areas() {
        assertEquals("1.50 m²", Units.area(1.5f, imperial = false))
        assertEquals("500 cm²", Units.area(0.05f, imperial = false))
        assertEquals("10.76 ft²", Units.area(1f, imperial = true))
    }
}
