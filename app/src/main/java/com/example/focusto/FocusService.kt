package com.example.focusto

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class FocusService : Service() {

    companion object {
        // --- Acciones de Intent para controlar el servicio ---
        const val ACTION_START_FOCUS  = "START_FOCUS"
        const val ACTION_STOP_FOCUS   = "STOP_FOCUS"
        const val ACTION_PAUSE_FOCUS  = "PAUSE_FOCUS"
        const val ACTION_RESUME_FOCUS = "RESUME_FOCUS"

        // --- Broadcasts que emite el servicio hacia MainActivity ---
        /** Tick del temporizador (cada segundo) */
        const val ACTION_TIMER_TICK     = "com.example.focusto.TIMER_TICK"
        /** El temporizador llegó a 0 (sesión o descanso terminado) */
        const val ACTION_TIMER_FINISH   = "com.example.focusto.TIMER_FINISH"
        /** Actualización del motivador (cada 5 min) */
        const val ACTION_PROGRESS_UPDATE = "com.example.focusto.PROGRESS_UPDATE"

        // --- Extras de los broadcasts ---
        const val EXTRA_MILLIS_LEFT   = "millis_left"
        const val EXTRA_TOTAL_MILLIS  = "total_millis"
        const val EXTRA_IS_BREAK      = "is_break"
        const val EXTRA_MILLIS_ELAPSED = "millis_elapsed"

        private const val CHANNEL_ID      = "FocusServiceChannel"
        private const val CHANNEL_ALERT_ID = "FocusAlertChannel"
        private const val NOTIFICATION_ID  = 101
        private const val ALERT_NOTIF_ID   = 102
    }

    // --- WakeLock: mantiene la CPU activa con pantalla apagada ---
    private var wakeLock: PowerManager.WakeLock? = null

    // --- Temporizador interno del servicio ---
    private var pomodoroTimer: CountDownTimer? = null
    private var millisLeft: Long = 25 * 60 * 1000L
    private var totalDuration: Long = 25 * 60 * 1000L
    private var isBreakTime: Boolean = false
    private var isRunning: Boolean = false
    private var lastMotivatorBroadcastMinute: Int = -1

    // --- Vigilancia de apps distractoras ---
    private val handler = Handler(Looper.getMainLooper())
    private val checkAppsInterval = 2000L

    private val blacklist = listOf(
        "com.zhiliaoapp.musically",   // TikTok
        "com.instagram.android",
        "com.facebook.katana",
        "com.whatsapp",
        "com.twitter.android",
        "com.snapchat.android",
        "com.google.android.youtube"
    )

    // --- Binder ---
    private val binder = LocalBinder()
    inner class LocalBinder : Binder() {
        fun getService(): FocusService = this@FocusService
    }
    override fun onBind(intent: Intent?): IBinder = binder

    // ─────────────────────────────────────────────
    //  Ciclo de vida del servicio
    // ─────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                val duration  = intent.getLongExtra(EXTRA_TOTAL_MILLIS, 25 * 60 * 1000L)
                val isBreak   = intent.getBooleanExtra(EXTRA_IS_BREAK, false)
                startFocusSession(duration, isBreak)
            }
            ACTION_STOP_FOCUS   -> stopFocusSession()
            ACTION_PAUSE_FOCUS  -> pauseTimer()
            ACTION_RESUME_FOCUS -> resumeTimer()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        pomodoroTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        super.onDestroy()
    }

    // ─────────────────────────────────────────────
    //  Sesión de enfoque
    // ─────────────────────────────────────────────

    private fun startFocusSession(durationMs: Long, breakTime: Boolean) {
        totalDuration  = durationMs
        millisLeft     = durationMs
        isBreakTime    = breakTime
        isRunning      = true
        lastMotivatorBroadcastMinute = -1

        val label = if (isBreakTime) "☕ Descanso activo" else "🎯 Sesión de Enfoque"
        val desc  = if (isBreakTime) "Relájate, vuelves en ${durationMs / 60000} min" else "Vigilando distracciones..."
        startForeground(NOTIFICATION_ID, createNotification(label, desc))

        startTimer(durationMs)
        if (!isBreakTime) startAppCheckLoop()   // Solo bloquear apps en sesión de estudio
    }

    private fun stopFocusSession() {
        isRunning = false
        pomodoroTimer?.cancel()
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        releaseWakeLock()
        stopSelf()
    }

    private fun pauseTimer() {
        isRunning = false
        pomodoroTimer?.cancel()
        updateNotification("⏸ Sesión pausada", "Pulsa continuar para retomar")
    }

    private fun resumeTimer() {
        isRunning = true
        startTimer(millisLeft)   // Retoma con el tiempo restante guardado
        updateNotification(
            if (isBreakTime) "☕ Descanso activo" else "🎯 Sesión de Enfoque",
            "Vigilando distracciones..."
        )
    }

    // ─────────────────────────────────────────────
    //  Temporizador interno
    // ─────────────────────────────────────────────

    private fun startTimer(durationMs: Long) {
        pomodoroTimer?.cancel()
        pomodoroTimer = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                millisLeft = millisUntilFinished
                broadcastTick(millisUntilFinished)
                updateNotificationTick(millisUntilFinished)
                checkMotivatorUpdate(millisUntilFinished)
            }
            override fun onFinish() {
                millisLeft = 0
                broadcastFinish()
                onSessionFinished()
            }
        }.start()
    }

    private fun onSessionFinished() {
        isRunning = false
        handler.removeCallbacksAndMessages(null)   // Detiene el bucle de vigilancia
        stopForeground(STOP_FOREGROUND_REMOVE)

        // Notificación audible de fin de sesión
        showAlertNotification(
            if (isBreakTime) "☕ ¡Descanso terminado!" else "✅ ¡Pomodoro completado!",
            if (isBreakTime) "Es hora de volver al estudio." else "Tómate un descanso de 5 minutos."
        )

        stopSelf()
    }

    // ─────────────────────────────────────────────
    //  Broadcasts hacia MainActivity
    // ─────────────────────────────────────────────

    private fun broadcastTick(millisLeft: Long) {
        sendBroadcast(Intent(ACTION_TIMER_TICK).apply {
            putExtra(EXTRA_MILLIS_LEFT,  millisLeft)
            putExtra(EXTRA_TOTAL_MILLIS, totalDuration)
            putExtra(EXTRA_IS_BREAK,     isBreakTime)
            setPackage(packageName)
        })
    }

    private fun broadcastFinish() {
        sendBroadcast(Intent(ACTION_TIMER_FINISH).apply {
            putExtra(EXTRA_IS_BREAK, isBreakTime)
            setPackage(packageName)
        })
    }

    private fun checkMotivatorUpdate(millisLeft: Long) {
        if (isBreakTime) return
        val elapsedMin = ((totalDuration - millisLeft) / 60_000L).toInt()
        if (elapsedMin > 0 && elapsedMin % 5 == 0 && elapsedMin != lastMotivatorBroadcastMinute) {
            lastMotivatorBroadcastMinute = elapsedMin
            sendBroadcast(Intent(ACTION_PROGRESS_UPDATE).apply {
                putExtra(EXTRA_MILLIS_ELAPSED, totalDuration - millisLeft)
                putExtra(EXTRA_TOTAL_MILLIS, totalDuration)
                setPackage(packageName)
            })
        }
    }

    // ─────────────────────────────────────────────
    //  Vigilancia de apps distractoras
    // ─────────────────────────────────────────────

    private fun startAppCheckLoop() {
        handler.post(object : Runnable {
            override fun run() {
                if (!isRunning) return
                checkTopApp()
                handler.postDelayed(this, checkAppsInterval)
            }
        })
    }

    private fun checkTopApp() {
        val usm   = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time  = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 10_000, time)
        if (!stats.isNullOrEmpty()) {
            val topApp = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return
            if (blacklist.contains(topApp)) {
                Log.d("FocusService", "Bloqueando app: $topApp")
                launchBlockOverlay()
            }
        }
    }

    private fun launchBlockOverlay() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("SHOW_BLOCK_OVERLAY", true)
            putExtra("BLOCK_MESSAGE_KEY",  "APP_BLOCKED")
        })
    }

    // ─────────────────────────────────────────────
    //  Notificaciones
    // ─────────────────────────────────────────────

    private fun createNotification(title: String, content: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, content: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun updateNotificationTick(millisLeft: Long) {
        val min = (millisLeft / 1000) / 60
        val sec = (millisLeft / 1000) % 60
        val label = if (isBreakTime) "☕ Descanso" else "🎯 Enfoque"
        updateNotification(label, "⏱ %02d:%02d restantes".format(min, sec))
    }

    private fun showAlertNotification(title: String, content: String) {
        val pi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Vibración de aviso: patrón largo para que se note con pantalla apagada
            .setVibrate(longArrayOf(0, 400, 200, 400, 200, 600))
            .build()
        NotificationManagerCompat.from(this).notify(ALERT_NOTIF_ID, notif)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            // Canal silencioso para el temporizador en curso
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Sesión de Enfoque", NotificationManager.IMPORTANCE_LOW)
            )
            // Canal con sonido/vibración para cuando termina la sesión
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ALERT_ID, "Alertas de Pomodoro", NotificationManager.IMPORTANCE_HIGH).also {
                    it.enableVibration(true)
                    it.vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 600)
                }
            )
        }
    }

    // ─────────────────────────────────────────────
    //  WakeLock
    // ─────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "FocusTo::PomodoroWakeLock"
        ).also { it.acquire(35 * 60 * 1000L) }  // máximo 35 min de seguridad
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }
}
