package dev.railwatch.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dev.railwatch.shared.domain.Attachment
import dev.railwatch.shared.parsing.TextItem
import dev.railwatch.shared.parsing.TicketDetails
import dev.railwatch.shared.parsing.TicketParser
import dev.railwatch.shared.parsing.TicketText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.UUID

/** Ticket PDFs live in the app's private files (tickets/<id>.pdf); nothing leaves the phone. */
class TicketFiles(private val context: Context) {
    private val dir = File(context.filesDir, "tickets").apply { mkdirs() }
    fun file(id: String) = File(dir, "$id.pdf")

    /** Copies a shared PDF into private storage; null if it isn't a PDF or is over 10 MB. */
    fun save(uri: Uri): Attachment? {
        val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "ticket.pdf"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > 10 * 1024 * 1024 || bytes.size < 5 || !bytes.copyOfRange(0, minOf(1024, bytes.size)).decodeToString().contains("%PDF-")) return null
        val id = UUID.randomUUID().toString()
        file(id).writeBytes(bytes)
        return Attachment(id = id, name = name.take(255), type = "application/pdf", size = bytes.size.toLong(), createdAt = Instant.now().toString())
    }

    fun delete(id: String) { file(id).delete() }

    /** Renders one page at the given width for viewing or reading. */
    fun render(id: String, page: Int, width: Int): Bitmap? = ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { pdf ->
            if (page >= pdf.pageCount) return null
            pdf.openPage(page).use { p ->
                val height = (width.toFloat() * p.height / p.width).toInt()
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { b -> b.eraseColor(Color.WHITE); p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
            }
        }
    }

    fun pageCount(id: String): Int = runCatching { ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { p -> p.pageCount } } }.getOrDefault(0)
}

/**
 * Reads an IRCTC ticket on the phone: renders the first pages, recognises the text (keeping word positions so table
 * rows and columns can be rebuilt, as the web app does) and decodes the QR code, then parses all of it.
 */
class TicketReader(private val files: TicketFiles) {
    private val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val qr = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())

    suspend fun read(id: String): TicketDetails = withContext(Dispatchers.Default) {
        val pages = minOf(files.pageCount(id), 3)
        val ocr = StringBuilder(); var code = ""
        for (n in 0 until pages) {
            val bitmap = files.render(id, n, 2000) ?: continue
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = runCatching { text.process(image).await() }.getOrNull()
            if (result != null) {
                // Word boxes → positioned items in PDF-point scale (a 2000px render of an A4 page is ~600pt wide, so ×0.3)
                // with y flipped → rows with header/value columns (same thresholds as the web), plus the raw text.
                val items = result.textBlocks.flatMap { b -> b.lines.flatMap { l -> l.elements.mapNotNull { e -> e.boundingBox?.let { r -> TextItem(e.text, r.left * 0.3, -r.centerY() * 0.3) } } } }
                ocr.append(TicketText.rows(items)).append('\n').append(result.text).append('\n')
            }
            if (code.isEmpty()) code = runCatching { qr.process(image).await().firstOrNull()?.rawValue }.getOrNull().orEmpty()
            bitmap.recycle()
        }
        merge(TicketParser.parse(ocr.toString()), if (code.isNotEmpty()) TicketParser.parse(code) else TicketDetails())
    }

    /** The QR's values win where both have one (it is machine-written); the page text fills the rest. */
    private fun merge(a: TicketDetails, b: TicketDetails) = TicketDetails(
        from = b.from ?: a.from, to = b.to ?: a.to, date = b.date ?: a.date, trainNumber = b.trainNumber ?: a.trainNumber, trainName = b.trainName ?: a.trainName,
        travelClass = b.travelClass ?: a.travelClass, pnr = b.pnr ?: a.pnr, coach = b.coach ?: a.coach, seat = b.seat ?: a.seat, berth = b.berth ?: a.berth,
        departure = b.departure ?: a.departure, arrival = b.arrival ?: a.arrival, arrivalDate = b.arrivalDate ?: a.arrivalDate, ticketStatus = b.ticketStatus ?: a.ticketStatus,
        bookingType = b.bookingType ?: a.bookingType, fare = b.fare ?: a.fare,
    )
}
