package com.example.focusto

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var rootLayout: RelativeLayout
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var tvSensorInfo: TextView
    private lateinit var btnOpenPdf: Button
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button
    private lateinit var pdfImageView: ImageView
    private lateinit var warmFilterOverlay: View

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    private var isNightModeActive: Boolean = false
    private var currentLuxValue: Int = 0

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private val selectPdfLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { openPdfFromUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            initViews()
            setupSensors()
            setupListeners()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al iniciar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        rootLayout = findViewById(R.id.rootLayout)
        tvStatusTitle = findViewById(R.id.tvStatusTitle)
        tvStatusDesc = findViewById(R.id.tvStatusDesc)
        tvSensorInfo = findViewById(R.id.tvSensorInfo)
        btnOpenPdf = findViewById(R.id.btnOpenPdf)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        pdfImageView = findViewById(R.id.pdfImageView)
        warmFilterOverlay = findViewById(R.id.warmFilterOverlay)

        pdfImageView.scaleType = ImageView.ScaleType.MATRIX
    }

    private fun setupSensors() {
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            tvSensorInfo.text = getString(R.string.sensor_unavailable)
        }
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            currentLuxValue = event.values[0].toInt()
            updateSensorAndPageText()

            if (currentLuxValue < 15 && !isNightModeActive) {
                toggleNightMode(true)
            } else if (currentLuxValue >= 15 && isNightModeActive) {
                toggleNightMode(false)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun setupListeners() {
        btnOpenPdf.setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }

        btnNextPage.setOnClickListener {
            if (totalPages > 0 && currentPageIndex < totalPages - 1) {
                currentPageIndex++
                renderPage(currentPageIndex)
            }
        }

        btnPrevPage.setOnClickListener {
            if (totalPages > 0 && currentPageIndex > 0) {
                currentPageIndex--
                renderPage(currentPageIndex)
            }
        }
    }

    private fun openPdfFromUri(uri: Uri) {
        try {
            closePdfRenderer()

            val inputStream = contentResolver.openInputStream(uri) ?: return
            val tempFile = File(cacheDir, "selected_doc.pdf")
            val outputStream = FileOutputStream(tempFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()

            fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
            fileDescriptor?.let { fd ->
                pdfRenderer = PdfRenderer(fd)
                totalPages = pdfRenderer?.pageCount ?: 0
                currentPageIndex = 0

                if (totalPages > 0) {
                    renderPage(currentPageIndex)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir el PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return

        try {
            currentPdfPage?.close()
            currentPdfPage = renderer.openPage(index)

            val page = currentPdfPage ?: return
            // Usamos un multiplicador estable de alta calidad
            val multiplier = 2
            val bitmap = createBitmap(
                width = page.width * multiplier,
                height = page.height * multiplier,
                config = Bitmap.Config.ARGB_8888
            )

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // Si el modo nocturno está activo, invertimos los colores del PDF para que el fondo sea oscuro y el texto claro
            val processedBitmap = if (isNightModeActive) {
                invertBitmapColors(bitmap)
            } else {
                bitmap
            }

            pdfImageView.setImageBitmap(processedBitmap)

            updateSensorAndPageText()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun invertBitmapColors(src: Bitmap): Bitmap {
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

            val invR = 255 - r
            val invG = 255 - g
            val invB = 255 - b

            pixels[i] = (a shl 24) or (invR shl 16) or (invG shl 8) or invB
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun updateSensorAndPageText() {
        val luxString = getString(R.string.sensor_lux_format, currentLuxValue)
        if (totalPages > 0) {
            tvSensorInfo.text = getString(
                R.string.page_info_format,
                luxString,
                currentPageIndex + 1,
                totalPages
            )
        } else {
            tvSensorInfo.text = luxString
        }
    }

    private fun toggleNightMode(enable: Boolean) {
        isNightModeActive = enable

        if (enable) {
            rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_night))
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_title_night))
            tvStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.text_desc_night))
            tvSensorInfo.setTextColor(ContextCompat.getColor(this, R.color.sensor_text_night))
            btnPrevPage.setTextColor(ContextCompat.getColor(this, R.color.text_title_night))
            btnNextPage.setTextColor(ContextCompat.getColor(this, R.color.text_title_night))
            warmFilterOverlay.visibility = View.GONE

            tvStatusTitle.text = getString(R.string.status_night_title)
            tvStatusDesc.text = getString(R.string.status_night_desc)
        } else {
            rootLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.bg_normal))
            tvStatusTitle.setTextColor(ContextCompat.getColor(this, R.color.text_title_normal))
            tvStatusDesc.setTextColor(ContextCompat.getColor(this, R.color.text_desc_normal))
            tvSensorInfo.setTextColor(ContextCompat.getColor(this, R.color.sensor_text_normal))
            btnPrevPage.setTextColor(ContextCompat.getColor(this, R.color.text_title_normal))
            btnNextPage.setTextColor(ContextCompat.getColor(this, R.color.text_title_normal))
            warmFilterOverlay.visibility = View.GONE

            tvStatusTitle.text = getString(R.string.status_title_normal)
            tvStatusDesc.text = getString(R.string.status_desc_normal)
        }

        if (totalPages > 0) {
            renderPage(currentPageIndex)
        }
    }

    private fun closePdfRenderer() {
        try {
            currentPdfPage?.close()
            currentPdfPage = null
            pdfRenderer?.close()
            pdfRenderer = null
            fileDescriptor?.close()
            fileDescriptor = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdfRenderer()
    }
}