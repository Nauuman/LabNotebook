package com.nauuman.measure.ui

import com.nauuman.measure.ar.MeasurementSnap
import com.nauuman.measure.data.Units

/** Human-readable descriptions of a measurement, shared by the detail card, copy and history. */
object Describe {
    fun title(m: MeasurementSnap): String = when {
        m.closed -> "Area"
        m.points.size <= 2 -> "Line"
        else -> "Path"
    }

    fun oneLine(m: MeasurementSnap, imperial: Boolean): String = when {
        m.closed -> "Area: ${Units.area(m.area, imperial)} · Perimeter: ${Units.length(m.totalLength, imperial)}"
        m.points.size <= 2 -> "Line: ${Units.length(m.totalLength, imperial)}"
        else -> "Path: ${m.segmentLengths.size} segments · Total: ${Units.length(m.totalLength, imperial)}"
    }

    fun details(m: MeasurementSnap, imperial: Boolean): List<String> = buildList {
        if (m.segmentLengths.size > 1) {
            m.segmentLengths.forEachIndexed { i, len -> add("Segment ${i + 1}: ${Units.length(len, imperial)}") }
            add((if (m.closed) "Perimeter: " else "Total: ") + Units.length(m.totalLength, imperial))
        } else {
            add("Length: ${Units.length(m.totalLength, imperial)}")
        }
        if (m.closed) add("Area: ${Units.area(m.area, imperial)}")
    }

    /** Summaries for every finished measurement in a capture. */
    fun summary(measurements: List<MeasurementSnap>, imperial: Boolean): List<String> =
        measurements.filter { it.points.size >= 2 }.map { oneLine(it, imperial) }
}
