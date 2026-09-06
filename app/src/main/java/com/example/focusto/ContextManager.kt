package com.example.focusto

import android.os.SystemClock

data class ContextState(
    val rawLux: Float,
    val rawAccel: Float
)

class ContextManager(private val onStateUpdated: (ContextState) -> Unit) {

    private var lastTouchTime: Long = 0
    private var darkStateStartTime: Long = 0
    private val darkConfirmationDelayMs = 1500L // Exige 1.5 segundos de oscuridad continua

    // Umbrales calibrados para el mundo real
    private val lightThresholdLux = 5.0f       // Requiere oscuridad casi total (< 5 lux)
    private val shakeThresholdAccel = 8.5f      // Requiere agitación firme (> 8.5 m/s²)

    fun processRawData(lux: Float, accel: Float) {
        val currentState = ContextState(rawLux = lux, rawAccel = accel)
        onStateUpdated(currentState)
    }

    fun registerUserTouch() {
        lastTouchTime = SystemClock.elapsedRealtime()
    }

    fun isUserActivelyReading(): Boolean {
        // Se considera lectura activa si tocó la pantalla en los últimos 10 segundos
        return (SystemClock.elapsedRealtime() - lastTouchTime) < 10000L
    }

    fun isDarkStable(lux: Float): Boolean {
        val currentTime = SystemClock.elapsedRealtime()
        if (lux < lightThresholdLux) {
            if (darkStateStartTime == 0L) {
                darkStateStartTime = currentTime
            }
            return (currentTime - darkStateStartTime) >= darkConfirmationDelayMs
        } else {
            darkStateStartTime = 0L
            return false
        }
    }

    fun isShaking(accel: Float): Boolean {
        return accel > shakeThresholdAccel
    }
}