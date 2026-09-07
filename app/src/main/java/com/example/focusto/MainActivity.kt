package com.example.focusto

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    private lateinit var ivFocusMotivator: ImageView

    // Pomodoro y Bloqueo
    private lateinit var tvPomodoroTimer: TextView
    private lateinit var btnStartPomodoro: Button
    private lateinit var btnResetPomodoro: Button
    private lateinit var layoutLockOverlay: RelativeLayout
    private lateinit var tvLockMessage: TextView
    private lateinit var btnUnlock: Button

    // El timer YA NO vive aquí — vive en FocusService (sobrevive pantalla apagada)
    private var isPomodoroRunning = false
    private var isBreakTime = false
    private var currentSessionDuration = 25 * 60 * 1000L  // para actualizar el motivador

    // --- Estado del Modo Estudio Físico ---
    private var isPdfLoaded = false
    private var physicalStudyModeNotified = false

    /**
     * Receiver unificado: escucha los 3 eventos que emite FocusService.
     *  - TIMER_TICK      → actualiza cronómetro en pantalla + motivador
     *  - TIMER_FINISH    → maneja fin de sesión / inicio de descanso
     *  - PROGRESS_UPDATE → actualiza motivador cada 5 min (respaldo)
     */
    private val focusServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                FocusService.ACTION_TIMER_TICK -> {
                    val left  = intent.getLongExtra(FocusService.EXTRA_MILLIS_LEFT, 0L)
                    val total = intent.getLongExtra(FocusService.EXTRA_TOTAL_MILLIS, currentSessionDuration)
                    val min   = (left / 1000) / 60
                    val sec   = (left / 1000) % 60
                    tvPomodoroTimer.text = String.format("%02d:%02d", min, sec)
                    updateMotivator(left, total)
                }
                FocusService.ACTION_TIMER_FINISH -> {
                    val wasBreak = intent.getBooleanExtra(FocusService.EXTRA_IS_BREAK, false)
                    onPomodoroFinished(wasBreak)
                }
                FocusService.ACTION_PROGRESS_UPDATE -> {
                    val elapsed = intent.getLongExtra(FocusService.EXTRA_MILLIS_ELAPSED, 0L)
                    val total   = intent.getLongExtra(FocusService.EXTRA_TOTAL_MILLIS, currentSessionDuration)
                    updateMotivator((total - elapsed).coerceAtLeast(0L), total)
                }
            }
        }
    }

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
            checkIntent(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al iniciar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkIntent(intent)
    }

    private fun checkIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("SHOW_BLOCK_OVERLAY", false) == true) {
            val messageKey = intent.getStringExtra("BLOCK_MESSAGE_KEY")
            val message = if (messageKey == "APP_BLOCKED") {
                getString(R.string.lock_message_app_blocked)
            } else {
                "¡REGRESA AL ESTUDIO!\nTu sesión sigue activa."
            }
            showLockOverlay(message)
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

        tvPomodoroTimer = findViewById(R.id.tvPomodoroTimer)
        btnStartPomodoro = findViewById(R.id.btnStartPomodoro)
        btnResetPomodoro = findViewById(R.id.btnResetPomodoro)
        layoutLockOverlay = findViewById(R.id.layoutLockOverlay)
        tvLockMessage = findViewById(R.id.tvLockMessage)
        btnUnlock = findViewById(R.id.btnUnlock)
        ivFocusMotivator = findViewById(R.id.ivFocusMotivator)

        pdfImageView.scaleType = ImageView.ScaleType.FIT_CENTER
        setupZoomGestures()
    }

    private fun setupConcentrationSystems() {
        contextManager = ContextManager { state ->
            runOnUiThread {
                currentLuxValue = state.rawLux.toInt()
                updateSensorAndPageText()

                // 1. Adaptación de Luz (Independiente)
                val nightNeeded = adaptationEngine.shouldEnableNightMode(state, contextManager.isUserActivelyReading(), contextManager)
                if (nightNeeded && !isNightModeActive) toggleNightMode(true)
                else if (!nightNeeded && isNightModeActive && state.rawLux >= 15.0f) toggleNightMode(false)

                // 2. Concentración y Salud
                if (isPomodoroRunning && !isBreakTime) {
                    val mode = adaptationEngine.evaluateConcentration(state, contextManager)
                    handleConcentrationMode(mode)

                    // --- INTUICIÓN: Detectar Modo Estudio Físico sin PDF ---
                    // Si el Pomodoro corre, no hay PDF y el celular está confirmado boca abajo,
                    // activamos el aviso de Modo Físico con vibración (una sola vez por sesión)
                    if (!isPdfLoaded && contextManager.isPhysicalStudyModeActive && !physicalStudyModeNotified) {
                        physicalStudyModeNotified = true
                        onPhysicalStudyModeActivated(state.rawLux)
                    } else if (!contextManager.isPhysicalStudyModeActive) {
                        // Resetear para que pueda notificar de nuevo si baja y sube
                        physicalStudyModeNotified = false
                    }
                } else {
                    hideHealthAlert()
                    physicalStudyModeNotified = false
                }
            }
        }

        sensorService = SensorService(this) { lux, accel, proximity, inclination ->
            contextManager.processRawData(lux, accel, proximity, inclination)
        }
    }

    /**
     * Callback de Intuición: Se llama cuando se confirma que el usuario estudia
     * sin PDF (Modo Físico / Estudio Externo). Vibra suavemente y muestra un hint.
     */
    private fun onPhysicalStudyModeActivated(currentLux: Float) {
        // Vibración sutil de confirmación (patrón corto-largo)
        vibrate(longArrayOf(0, 80, 60, 200))

        // Mostrar hint en el banner de salud (color verde como premio)
        val message = if (currentLux < 12.0f) {
            getString(R.string.physical_study_light_alert)
        } else {
            getString(R.string.physical_study_no_pdf_hint)
        }
        tvHealthAlert.text = message
        tvHealthAlert.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        tvHealthAlert.visibility = View.VISIBLE

        Toast.makeText(this, getString(R.string.physical_study_started), Toast.LENGTH_SHORT).show()
    }

    /**
     * Vibra con el patrón indicado, compatible con API 26+ y versiones anteriores.
     * @param pattern array en formato [delay, on, off, on, ...] en ms
     */
    private fun vibrate(pattern: LongArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, -1)
            }
        }
    }

    private fun handleConcentrationMode(mode: ConcentrationMode) {
        when (mode) {
            ConcentrationMode.DEEP_FOCUS -> {
                // Si hay PDF cargado, mostrar el banner de enfoque profundo normal
                // Si no hay PDF, la intuición se encarga (onPhysicalStudyModeActivated)
                if (isPdfLoaded) {
                    hideHealthAlert()
                    tvHealthAlert.text = "¡MODO ENFOQUE PROFUNDO ACTIVO!"
                    tvHealthAlert.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    tvHealthAlert.visibility = View.VISIBLE
                }
            }
            ConcentrationMode.COOL_DOWN_LOCK -> {
                showLockOverlay(getString(R.string.lock_message))
            }
            ConcentrationMode.FACE_ABSENT -> {
                pausePomodoro()
                showLockOverlay(getString(R.string.alert_face_absent))
            }
            ConcentrationMode.TOO_CLOSE -> {
                showHealthAlert(getString(R.string.alert_too_close))
            }
            ConcentrationMode.BAD_POSTURE -> {
                showHealthAlert(getString(R.string.alert_bad_posture))
            }
            ConcentrationMode.NORMAL -> {
                hideHealthAlert()
            }
        }
    }

    private fun showLockOverlay(message: String) {
        tvLockMessage.text = message
        layoutLockOverlay.visibility = View.VISIBLE
        pausePomodoro()
    }

    private fun showHealthAlert(message: String) {
        tvHealthAlert.text = message
        tvHealthAlert.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
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
                if (totalPages > 0) renderPage(currentPageIndex)
            }
        })

        val touchListener = View.OnTouchListener { v, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.pointerCount > 1) v.parent.requestDisallowInterceptTouchEvent(true)
            false
        }

        verticalScroll.setOnTouchListener(touchListener)
        rootLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) contextManager.registerUserTouch()
            false
        }
    }

    override fun onResume() {
        super.onResume()
        sensorService.startListening()
        // Registrar el receiver unificado: tick + finish + motivador
        val filter = IntentFilter().apply {
            addAction(FocusService.ACTION_TIMER_TICK)
            addAction(FocusService.ACTION_TIMER_FINISH)
            addAction(FocusService.ACTION_PROGRESS_UPDATE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(focusServiceReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(focusServiceReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorService.stopListening()
        try { unregisterReceiver(focusServiceReceiver) } catch (_: IllegalArgumentException) {}
    }

    private fun setupListeners() {
        btnOpenPdf.setOnClickListener { selectPdfLauncher.launch("application/pdf") }
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
        btnStartPomodoro.setOnClickListener { if (isPomodoroRunning) pausePomodoro() else startPomodoro() }
        btnResetPomodoro.setOnClickListener { resetPomodoro() }
        btnUnlock.setOnClickListener {
            layoutLockOverlay.visibility = View.GONE
        }
    }

    private fun startPomodoro() {
        if (!checkPermissions()) return
        isPomodoroRunning = true
        physicalStudyModeNotified = false
        btnStartPomodoro.text = "PAUSAR"
        setSilentMode(true)

        currentSessionDuration = if (isBreakTime) 5 * 60 * 1000L else 25 * 60 * 1000L
        sendToFocusService(FocusService.ACTION_START_FOCUS, currentSessionDuration, isBreakTime)
    }

    /**
     * Llamado cuando el FocusService emite ACTION_TIMER_FINISH.
     * Si terminó una sesión de estudio → inicia descanso de 5 min automáticamente.
     * Si terminó un descanso → vuelve al estado inicial listo para otra sesión.
     */
    private fun onPomodoroFinished(wasBreak: Boolean) {
        setSilentMode(false)
        isPomodoroRunning = false

        if (!wasBreak) {
            // Terminó sesión de estudio → inicia descanso automáticamente
            isBreakTime = true
            Toast.makeText(this, "✅ ¡Pomodoro completado! Descansa 5 minutos.", Toast.LENGTH_LONG).show()
            btnStartPomodoro.text = "PAUSAR"
            currentSessionDuration = 5 * 60 * 1000L
            tvPomodoroTimer.text = "05:00"
            updateMotivator(0L, 25 * 60 * 1000L)   // motivador en estado máximo (flor/constelación)
            isPomodoroRunning = true
            setSilentMode(false)
            sendToFocusService(FocusService.ACTION_START_FOCUS, currentSessionDuration, true)
        } else {
            // Terminó descanso → volver a estado inicial
            isBreakTime = false
            Toast.makeText(this, "☕ ¡Descanso terminado! Listo para otra sesión.", Toast.LENGTH_LONG).show()
            btnStartPomodoro.text = "INICIAR"
            tvPomodoroTimer.text = "25:00"
            currentSessionDuration = 25 * 60 * 1000L
            updateMotivator(25 * 60 * 1000L, 25 * 60 * 1000L)   // motivador al inicio (semilla)
        }
    }

    private fun pausePomodoro() {
        isPomodoroRunning = false
        btnStartPomodoro.text = "CONTINUAR"
        setSilentMode(false)
        sendToFocusService(FocusService.ACTION_PAUSE_FOCUS)
    }

    private fun resetPomodoro() {
        isPomodoroRunning = false
        isBreakTime = false
        btnStartPomodoro.text = "INICIAR"
        tvPomodoroTimer.text = "25:00"
        currentSessionDuration = 25 * 60 * 1000L
        setSilentMode(false)
        sendToFocusService(FocusService.ACTION_STOP_FOCUS)
        updateMotivator(25 * 60 * 1000L, 25 * 60 * 1000L)
    }

    // ─── Helpers de comunicación con FocusService ───────────────────────────

    /** Envía cualquier acción al FocusService, opcionalmente con duración y tipo de sesión */
    private fun sendToFocusService(
        action: String,
        durationMs: Long = currentSessionDuration,
        isBreak: Boolean = isBreakTime
    ) {
        val intent = Intent(this, FocusService::class.java).apply {
            this.action = action
            putExtra(FocusService.EXTRA_TOTAL_MILLIS, durationMs)
            putExtra(FocusService.EXTRA_IS_BREAK, isBreak)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent)
        else startService(intent)
    }

    // ─── Permisos ────────────────────────────────────────────────────────────

    private fun checkPermissions(): Boolean {
        if (!isUsageStatsPermissionGranted()) {
            Toast.makeText(this, "Permite el acceso a datos de uso para el bloqueo", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Permite mostrar sobre otras apps para el bloqueo", Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return false
        }
        return true
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    // ─── Motivador visual ────────────────────────────────────────────────────

    private fun updateMotivator(millisLeft: Long, totalTime: Long) {
        val progress = 1.0f - (millisLeft.toFloat() / totalTime.coerceAtLeast(1L))
        val stage = (progress * 4).toInt().coerceIn(0, 3)
        val resId = if (isNightModeActive) {
            when (stage) {
                0    -> R.drawable.ic_focus_star1
                1    -> R.drawable.ic_focus_star1
                2    -> R.drawable.ic_focus_star2
                else -> R.drawable.ic_focus_constellation
            }
        } else {
            when (stage) {
                0    -> R.drawable.ic_focus_seed
                1    -> R.drawable.ic_focus_sprout
                2    -> R.drawable.ic_focus_leaf
                else -> R.drawable.ic_focus_flower
            }
        }
        ivFocusMotivator.setImageResource(resId)
        ivFocusMotivator.visibility = View.VISIBLE
    }

    private fun setSilentMode(enable: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
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
                    isPdfLoaded = true // ← El usuario tiene material digital; desactiva la intuición física
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                currentPdfPage?.close()
                currentPdfPage = renderer.openPage(index)
                val page = currentPdfPage ?: return@launch
                val multiplier = currentZoomLevel
                val bitmap = createBitmap((page.width * multiplier).toInt(), (page.height * multiplier).toInt(), Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val processedBitmap = if (isNightModeActive) invertBitmapColors(bitmap) else bitmap
                withContext(Dispatchers.Main) {
                    pdfImageView.scaleX = 1.0f
                    pdfImageView.scaleY = 1.0f
                    pdfImageView.setImageBitmap(processedBitmap)
                    updateSensorAndPageText()
                }
            } catch (e: Exception) { e.printStackTrace() }
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
        tvSensorInfo.text = if (totalPages > 0) getString(R.string.page_info_format, luxString, currentPageIndex + 1, totalPages) else luxString
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
            isPdfLoaded = false
            totalPages = 0
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        closePdfRenderer()
    }
}