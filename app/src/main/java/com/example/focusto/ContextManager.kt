package com.example.focusto

import android.os.SystemClock

data class ContextState(
    val rawLux: Float,
    val rawAccel: Float,
    val rawProximity: Float,
    val rawInclination: Float
)

class ContextManager(private val onStateUpdated: (ContextState) -> Unit) {

    private var lastTouchTime: Long = 0
    private var darkStateStartTime: Long = 0
    private val darkConfirmationDelayMs = 1000L

    // Estado confirmado del Modo Estudio Físico (boca abajo)
    private var faceDownStartTime: Long = 0
    private val faceDownConfirmationDelayMs = 1500L
    private var _isPhysicalStudyModeActive: Boolean = false
    val isPhysicalStudyModeActive: Boolean get() = _isPhysicalStudyModeActive

    // Umbrales calibrados
    private val lightThresholdLux = 12.0f
    private val shakeThresholdAccel = 15.0f
    
    // Nota: El sensor de proximidad en la mayoría de celulares reacciona a < 5cm.
    // Usamos un umbral genérico que detecte la activación del sensor.
    private val proximityThreshold = 4.0f
    private val postureThresholdAngle = 45.0f

    fun processRawData(lux: Float, accel: Float, proximity: Float, inclination: Float) {
        val currentState = ContextState(
            rawLux = lux, 
            rawAccel = accel, 
            rawProximity = proximity, 
            rawInclination = inclination
        )
        // Actualizar el estado del Modo Físico antes de notificar
        updatePhysicalStudyMode(inclination)
        onStateUpdated(currentState)
    }

    /**
     * Detecta con confirmación temporal si el celular está boca abajo (inclinación > 150°).
     * Requiere que permanezca en esa posición por [faceDownConfirmationDelayMs] ms.
     */
    private fun updatePhysicalStudyMode(inclination: Float) {
        val currentTime = SystemClock.elapsedRealtime()
        val isFaceDown = inclination > 150.0f
        if (isFaceDown) {
            if (faceDownStartTime == 0L) {
                faceDownStartTime = currentTime
            }
            _isPhysicalStudyModeActive = (currentTime - faceDownStartTime) >= faceDownConfirmationDelayMs
        } else {
            faceDownStartTime = 0L
            _isPhysicalStudyModeActive = false
        }
    }

    fun registerUserTouch() {
        lastTouchTime = SystemClock.elapsedRealtime()
    }

    fun isUserActivelyReading(): Boolean {
        return (SystemClock.elapsedRealtime() - lastTouchTime) < 15000L
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

    fun isTooClose(proximity: Float): Boolean {
        // Alertamos si el sensor detecta que algo está cerca
        return proximity < proximityThreshold
    }

    fun hasBadPosture(inclination: Float): Boolean {
        // Ángulo de inclinación saludable: > 45 grados respecto a la mesa
        return inclination < postureThresholdAngle && inclination > 15.0f
    }
}
