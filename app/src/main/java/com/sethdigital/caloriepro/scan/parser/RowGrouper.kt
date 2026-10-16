package com.sethdigital.caloriepro.scan.parser

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class Point(val x: Float, val y: Float)

/**
 * One line of recognized text and its four corners, clockwise from top-left
 * (the order ML Kit reports them in).
 */
data class OcrLine(val text: String, val corners: List<Point>) {
    init {
        require(corners.size == 4) { "OcrLine needs exactly 4 corners" }
    }

    companion object {
        fun fromBox(text: String, left: Float, top: Float, right: Float, bottom: Float) = OcrLine(
            text,
            listOf(Point(left, top), Point(right, top), Point(right, bottom), Point(left, bottom)),
        )
    }
}

/**
 * Text recognizers split a nutrition label into separate pieces: "Calories" on
 * the left and a big "230" on the right usually come back as two lines, often
 * in different blocks. This rebuilds the visual rows of the label by grouping
 * lines that sit at the same height, after undoing any tilt in the photo.
 */
object RowGrouper {

    private class Box(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerY get() = (top + bottom) / 2f
    }

    private class Row(val seed: Box) {
        val members = mutableListOf(seed)
    }

    fun group(lines: List<OcrLine>): List<String> {
        val usable = lines.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return emptyList()

        val angle = medianAngle(usable)
        val boxes = usable.map { toUprightBox(it, -angle) }.filter { it.bottom > it.top }

        val rows = mutableListOf<Row>()
        for (box in boxes.sortedBy { it.centerY }) {
            // A line belongs to a row when each one's vertical center falls
            // inside the other's span. Requiring both directions stops a tall
            // number from swallowing the small line above it.
            val row = rows
                .filter { r ->
                    box.centerY in r.seed.top..r.seed.bottom &&
                        r.seed.centerY in box.top..box.bottom
                }
                .minByOrNull { abs(it.seed.centerY - box.centerY) }
            if (row != null) row.members += box else rows += Row(box)
        }

        return rows
            .sortedBy { r -> r.members.minOf { it.top } }
            .map { r -> r.members.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }

    /** Tilt of the text in radians, using the median so stray lines don't skew it. */
    private fun medianAngle(lines: List<OcrLine>): Double {
        val angles = lines
            .filter { it.text.trim().length >= 3 }
            .map { line ->
                val (tl, tr) = line.corners
                atan2((tr.y - tl.y).toDouble(), (tr.x - tl.x).toDouble())
            }
            .sorted()
        return if (angles.isEmpty()) 0.0 else angles[angles.size / 2]
    }

    private fun toUprightBox(line: OcrLine, rotation: Double): Box {
        val c = cos(rotation)
        val s = sin(rotation)
        val pts = line.corners.map { p -> Pair(p.x * c - p.y * s, p.x * s + p.y * c) }
        return Box(
            text = line.text,
            left = pts.minOf { it.first }.toFloat(),
            top = pts.minOf { it.second }.toFloat(),
            right = pts.maxOf { it.first }.toFloat(),
            bottom = pts.maxOf { it.second }.toFloat(),
        )
    }
}
