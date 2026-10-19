package com.sethdigital.caloriepro.scan

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sethdigital.caloriepro.data.Barcodes
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
    /** A product barcode found in the same photo, normalized. */
    val barcode: String? = null,
)

/** A detector for the barcodes printed on grocery products (EAN and UPC). */
fun productBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
        .build()
)

/** The first product barcode in [barcodes], normalized. */
fun firstProductCode(barcodes: List<Barcode>?): String? =
    barcodes?.firstNotNullOfOrNull { Barcodes.normalize(it.rawValue) }

/**
 * Reads a nutrition label, and any product barcode, from an image. Recognition
 * runs entirely on the phone using the models bundled in the app.
 */
class LabelScanner : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val barcodes = productBarcodeScanner()

    suspend fun scan(image: InputImage): ScanResult {
        val text = recognizer.process(image).await()
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { it.toOcrLine() }
        // A barcode is a bonus; failing to read one must not fail the label scan.
        val barcode = runCatching { firstProductCode(barcodes.process(image).await()) }.getOrNull()
        return ScanResult(
            facts = NutritionLabelParser.parse(lines),
            rows = RowGrouper.group(lines),
            barcode = barcode,
        )
    }

    override fun close() {
        recognizer.close()
        barcodes.close()
    }

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
