package com.example.focusto.config

/**
 * Configuración de FocusTo: valores que hoy están fijos en el código (duraciones del
 * Pomodoro, umbrales de sensores, lista de apps bloqueadas) y que una futura pantalla
 * de ajustes podría dejar editar. Los valores por defecto son exactamente los que la
 * app ya usaba antes de tener esta clase, así que el comportamiento no cambia.
 */
data class FocusConfig(
    val studyDurationMs: Long = 25 * 60 * 1000L,
    val breakDurationMs: Long = 5 * 60 * 1000L,
    val lightThresholdLux: Float = 12.0f,
    val shakeThresholdAccel: Float = 15.0f,
    val proximityThreshold: Float = 4.0f,
    val postureThresholdAngle: Float = 45.0f,
    val blockedApps: Set<String> = BlockableApp.entries.map { it.packageName }.toSet()
)
