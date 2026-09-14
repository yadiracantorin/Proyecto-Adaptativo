package com.example.focusto.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * Encapsula la apertura y el renderizado de un PDF con [PdfRenderer].
 * No conoce Views ni el ciclo de vida de la Activity: quien la usa decide en qué hilo
 * llamar a [renderPage] (es una operación pesada, pensada para correr fuera del hilo principal).
 */
class PdfViewer(private val context: Context) {

    private var pdfRenderer: PdfRenderer? = null
    private var currentPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    val pageCount: Int
        get() = pdfRenderer?.pageCount ?: 0

    /** Copia el PDF de [uri] a caché y lo abre. Devuelve la cantidad de páginas (0 si no se pudo abrir). */
    fun open(uri: Uri): Int {
        close()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return 0
        val tempFile = File(context.cacheDir, "selected_doc.pdf")
        inputStream.use { input ->
            FileOutputStream(tempFile).use { output -> input.copyTo(output) }
        }
        fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = fileDescriptor?.let { PdfRenderer(it) }
        return pageCount
    }

    /**
     * Renderiza la página [index] al [zoom] indicado. Es una operación pesada: llamarla
     * fuera del hilo principal. Devuelve `null` si no hay PDF abierto o el índice es inválido.
     */
    fun renderPage(index: Int, zoom: Float, invertColors: Boolean): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (index < 0 || index >= renderer.pageCount) return null
        currentPage?.close()
        currentPage = renderer.openPage(index)
        val page = currentPage ?: return null
        val bitmap = createBitmap((page.width * zoom).toInt(), (page.height * zoom).toInt(), Bitmap.Config.ARGB_8888)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return if (invertColors) invertColors(bitmap) else bitmap
    }

    /** Libera los recursos nativos del PDF actual (si hay alguno abierto). */
    fun close() {
        currentPage?.close()
        currentPage = null
        pdfRenderer?.close()
        pdfRenderer = null
        fileDescriptor?.close()
        fileDescriptor = null
    }

    private fun invertColors(src: Bitmap): Bitmap {
        val width = src.width
        val height = src.height
        val output = createBitmap(width, height, src.config ?: Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val a = pixel shr 24 and 0xff
            val r = pixel shr 16 and 0xff
            val g = pixel shr 8 and 0xff
            val b = pixel and 0xff
            pixels[i] = (a shl 24) or ((255 - r) shl 16) or ((255 - g) shl 8) or (255 - b)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }
}
