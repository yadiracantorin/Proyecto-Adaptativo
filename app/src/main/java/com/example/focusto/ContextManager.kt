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
    private val darkConfirmationDelayMs = 1500L

    // Umbrales calibrados
    private val lightThresholdLux = 8.0f
    private val shakeThresholdAccel = 12.0f
    
    // Umbrales de salud y enfoque (Basados en ángulo 0-90)
    // Nota: El sensor de proximidad en la mayoría de celulares es binario (Cerca=0, Lejos=5 o 8).
    // Si el hardware lo permite, 25cm sería ideal, pero usualmente reacciona a < 5cm.
    private val proximityThresholdTooClose = 25.0f 
    private val postureThresholdAngle = 45.0f    // Menos de 45 grados es mala postura (mirar abajo)

    fun processRawData(lux: Float, accel: Float, proximity: Float, inclination: Float) {
        val currentState = ContextState(
            rawLux = lux, 
            rawAccel = accel, 
            rawProximity = proximity, 
            rawInclination = inclination
        )
        onStateUpdated(currentState)
    }

    fun registerUserTouch() {
        lastTouchTime = SystemClock.elapsedRealtime()
    }

    fun isUserActivelyReading(): Boolean {
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

    fun isTooClose(proximity: Float): Boolean {
        return proximity < proximityThresholdTooClose
    }

    fun hasBadPosture(inclination: Float): Boolean {
        // En nuestro nuevo sistema: 0 es horizontal (mesa), 90 es vertical (cara)
        // Alertamos si el ángulo es menor a 45 (demasiado inclinado hacia abajo)
        // Pero evitamos alertar si está casi totalmente plano (mesa), eso lo maneja FACE_ABSENT
        return inclination < postureThresholdAngle && inclination > 15.0f
    }

    fun isFaceAbsent(inclination: Float, accel: Float): Boolean {
        // Ausencia si está boca abajo (ángulo > 160) 
        // O si está en una superficie plana (ángulo < 15) Y no se mueve (accel < 0.2)
        val isFaceDown = inclination > 160.0f
        val isOnTable = inclination < 15.0f && accel < 0.2f
        return isFaceDown || isOnTable
    }
}