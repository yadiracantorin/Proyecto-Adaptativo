package com.example.focusto

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var rootLayout: RelativeLayout
    private lateinit var tvStatusTitle: TextView
    private lateinit var tvStatusDesc: TextView
    private lateinit var tvSensorInfo: TextView
    private lateinit var tvHealthAlert: TextView
    private lateinit var btnOpenPdf: Button
    private lateinit var btnPrevPage: Button
    private lateinit var btnNextPage: Button
    private lateinit var pdfImageView: ImageView
    private lateinit var warmFilterOverlay: View
    private lateinit var verticalScroll: ScrollView
    private lateinit var horizontalScroll: HorizontalScrollView

    // Pomodoro y Bloqueo
    private lateinit var tvPomodoroTimer: TextView
    private lateinit var btnStartPomodoro: Button
    private lateinit var btnResetPomodoro: Button
    private lateinit var layoutLockOverlay: RelativeLayout
    private lateinit var tvLockMessage: TextView
    private lateinit var btnUnlock: Button
    
    // Alertas de Salud
    private var isShowingHealthAlert = false

    private var countDownTimer: CountDownTimer? = null
    private var isPomodoroRunning = false
    private var isBreakTime = false

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentZoomLevel: Float = 2.0f
    private val minZoomLevel = 1.0f
    private val maxZoomLevel = 5.0f

    private var pdfRenderer: PdfRenderer? = null
    private var currentPdfPage: PdfRenderer.Page? = null
    private var fileDescriptor: ParcelFileDescriptor? = null

    private var currentPageIndex: Int = 0
    private var totalPages: Int = 0
    private var isNightModeActive: Boolean = false
    private var currentLuxValue: Int = 0

    private lateinit var sensorService: SensorService
    private lateinit var contextManager: ContextManager
    private val adaptationEngine = AdaptationEngine()

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
            setupConcentrationSystems()
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
        tvHealthAlert = findViewById(R.id.tvHealthAlert)
        btnOpenPdf = findViewById(R.id.btnOpenPdf)
        btnPrevPage = findViewById(R.id.btnPrevPage)
        btnNextPage = findViewById(R.id.btnNextPage)
        pdfImageView = findViewById(R.id.pdfImageView)
        warmFilterOverlay = findViewById(R.id.warmFilterOverlay)
        verticalScroll = findViewById(R.id.verticalScrollPdf)
        horizontalScroll = findViewById(R.id.horizontalScrollPdf)

        // Pomodoro
        tvPomodoroTimer = findViewById(R.id.tvPomodoroTimer)
        btnStartPomodoro = findViewById(R.id.btnStartPomodoro)
        btnResetPomodoro = findViewById(R.id.btnResetPomodoro)
        layoutLockOverlay = findViewById(R.id.layoutLockOverlay)
        tvLockMessage = findViewById(R.id.tvLockMessage)
        btnUnlock = findViewById(R.id.btnUnlock)

        pdfImageView.scaleType = ImageView.ScaleType.FIT_CENTER

        setupZoomGestures()
    }

    private fun setupConcentrationSystems() {
        contextManager = ContextManager { state ->
            runOnUiThread {
                currentLuxValue = state.rawLux.toInt()
                updateSensorAndPageText()

                val mode = adaptationEngine.evaluateAdaptation(state, contextManager.isUserActivelyReading(), contextManager)
                
                when (mode) {
                    AdaptationMode.NIGHT_READING -> {
                        if (!isNightModeActive) toggleNightMode(true)
                        hideHealthAlert()
                    }
                    AdaptationMode.COOL_DOWN_LOCK -> {
                        if (isPomodoroRunning && !isBreakTime) {
                            showLockOverlay(getString(R.string.lock_message))
                        }
                    }
                    AdaptationMode.TOO_CLOSE -> {
                        if (isPomodoroRunning && !isBreakTime) {
                            showHealthAlert(getString(R.string.alert_too_close))
                        }
                    }
                    AdaptationMode.BAD_POSTURE -> {
                        if (isPomodoroRunning && !isBreakTime) {
                            showHealthAlert(getString(R.string.alert_bad_posture))
                        }
                    }
                    AdaptationMode.FACE_ABSENT -> {
                        if (isPomodoroRunning && !isBreakTime) {
                            pausePomodoro()
                            showLockOverlay(getString(R.string.alert_face_absent))
                        }
                    }
                    AdaptationMode.NORMAL -> {
                        if (isNightModeActive && state.rawLux >= 15.0f) toggleNightMode(false)
                        hideHealthAlert()
                    }
                }
            }
        }

        sensorService = SensorService(this) { lux, accel, proximity, inclination ->
            contextManager.processRawData(lux, accel, proximity, inclination)
        }
    }

    private fun showLockOverlay(message: String) {
        tvLockMessage.text = message
        layoutLockOverlay.visibility = View.VISIBLE
        pausePomodoro()
    }

    private fun showHealthAlert(message: String) {
        tvHealthAlert.text = message
        tvHealthAlert.visibility = View.VISIBLE
    }

    private fun hideHealthAlert() {
        tvHealthAlert.visibility = View.GONE
    }

    private fun setupZoomGestures() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                pdfImageView.scaleX *= scaleFactor
                pdfImageView.scaleY *= scaleFactor
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                currentZoomLevel *= pdfImageView.scaleX
                currentZoomLevel = currentZoomLevel.coerceIn(minZoomLevel, maxZoomLevel)
                if (totalPages > 0) {
                    renderPage(currentPageIndex)
                }
            }
        })

        val touchListener = View.OnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.pointerCount > 1) {
                v.parent.requestDisallowInterceptTouchEvent(true)
            }
            false
        }

        verticalScroll.setOnTouchListener(touchListener)
        
        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                contextManager.registerUserTouch()
            }
            false
        }
    }

    override fun onResume() {
        super.onResume()
        sensorService.startListening()
    }

    override fun onPause() {
        super.onPause()
        sensorService.stopListening()
    }

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

        btnStartPomodoro.setOnClickListener {
            if (isPomodoroRunning) pausePomodoro() else startPomodoro()
        }

        btnResetPomodoro.setOnClickListener {
            resetPomodoro()
        }

        btnUnlock.setOnClickListener {
            layoutLockOverlay.visibility = View.GONE
            isShowingHealthAlert = false
        }
    }

    private fun startPomodoro() {
        isPomodoroRunning = true
        btnStartPomodoro.text = "PAUSAR"
        setSilentMode(true)
        val time = if (isBreakTime) 5 * 60 * 1000L else 25 * 60 * 1000L
        
        countDownTimer = object : CountDownTimer(time, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                tvPomodoroTimer.text = String.format("%02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                isBreakTime = !isBreakTime
                Toast.makeText(this@MainActivity, 
                    if (isBreakTime) "¡Tiempo de descanso!" else "¡A estudiar!", 
                    Toast.LENGTH_LONG).show()
                resetPomodoro()
            }
        }.start()
    }

    private fun pausePomodoro() {
        isPomodoroRunning = false
        btnStartPomodoro.text = "CONTINUAR"
        countDownTimer?.cancel()
        setSilentMode(false)
    }

    private fun resetPomodoro() {
        pausePomodoro()
        btnStartPomodoro.text = "INICIAR"
        tvPomodoroTimer.text = "25:00"
        isBreakTime = false
        setSilentMode(false)
    }

    private fun setSilentMode(enable: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                if (enable) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                } else {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
            } else if (enable) {
                try {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    startActivity(intent)
                    Toast.makeText(this, "Por favor concede permiso para el Modo Silencio Total", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
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
                if (totalPages > 0) renderPage(currentPageIndex)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir el PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentPdfPage?.close()
                currentPdfPage = renderer.openPage(index)
                val page = currentPdfPage ?: return@launch
                
                val multiplier = currentZoomLevel
                val bitmap = createBitmap(
                    width = (page.width * multiplier).toInt(),
                    height = (page.height * multiplier).toInt(),
                    config = Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val processedBitmap = if (isNightModeActive) invertBitmapColors(bitmap) else bitmap

                withContext(Dispatchers.Main) {
                    pdfImageView.scaleX = 1.0f
                    pdfImageView.scaleY = 1.0f
                    pdfImageView.setImageBitmap(processedBitmap)
                    updateSensorAndPageText()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
            pixels[i] = (a shl 24) or ((255 - r) shl 16) or ((255 - g) shl 8) or (255 - b)
        }
        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    private fun updateSensorAndPageText() {
        val luxString = getString(R.string.sensor_lux_format, currentLuxValue)
        tvSensorInfo.text = if (totalPages > 0) {
            getString(R.string.page_info_format, luxString, currentPageIndex + 1, totalPages)
        } else luxString
    }

    private fun toggleNightMode(enable: Boolean) {
        isNightModeActive = enable
        val bgColor = ContextCompat.getColor(this, if (enable) R.color.bg_night else R.color.bg_normal)
        val titleColor = ContextCompat.getColor(this, if (enable) R.color.text_title_night else R.color.text_title_normal)
        val descColor = ContextCompat.getColor(this, if (enable) R.color.text_desc_night else R.color.text_desc_normal)
        val sensorColor = ContextCompat.getColor(this, if (enable) R.color.sensor_text_night else R.color.sensor_text_normal)

        rootLayout.setBackgroundColor(bgColor)
        tvStatusTitle.setTextColor(titleColor)
        tvStatusDesc.setTextColor(descColor)
        tvSensorInfo.setTextColor(sensorColor)
        btnPrevPage.setTextColor(titleColor)
        btnNextPage.setTextColor(titleColor)
        
        tvStatusTitle.text = getString(if (enable) R.string.status_night_title else R.string.status_title_normal)
        tvStatusDesc.text = getString(if (enable) R.string.status_night_desc else R.string.status_desc_normal)

        if (totalPages > 0) renderPage(currentPageIndex)
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