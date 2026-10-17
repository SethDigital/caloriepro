package com.sethdigital.caloriepro.scan

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sethdigital.caloriepro.scan.parser.NutritionFacts
import com.sethdigital.caloriepro.scan.parser.NutritionLabelParser
import com.sethdigital.caloriepro.scan.parser.OcrLine
import com.sethdigital.caloriepro.scan.parser.RowGrouper
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.sethdigital.caloriepro.scan.parser.Point as LabelPoint

data class ScanResult(
    val facts: NutritionFacts,
    /** The label text as it was read, row by row, so the user can double-check it. */
    val rows: List<String>,
)

/**
 * Reads a nutrition label from an image. Text recognition runs entirely on
 * the phone using the model bundled in the app.
 */
class LabelScanner : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun scan(image: InputImage): ScanResult {
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { it.toOcrLine() }
        return ScanResult(
            facts = NutritionLabelParser.parse(lines),
            rows = RowGrouper.group(lines),
        )
    }

    override fun close() = recognizer.close()

    private fun Text.Line.toOcrLine(): OcrLine? {
        val points = cornerPoints
        if (points != null && points.size == 4) {
            return OcrLine(text, points.map { LabelPoint(it.x.toFloat(), it.y.toFloat()) })
        }
        val box = boundingBox ?: return null
        return OcrLine.fromBox(text, box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat())
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
