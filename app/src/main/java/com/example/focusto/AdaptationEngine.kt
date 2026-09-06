package com.example.focusto

enum class AdaptationMode {
    NORMAL,
    NIGHT_READING,
    COOL_DOWN_LOCK
}

class AdaptationEngine {

    fun evaluateAdaptation(state: ContextState, isReading: Boolean): AdaptationMode {
        val manager = ContextManager {}

        // Prioridad 1: Detección de agitación violenta (Bloqueo anti-procrastinación)
        if (manager.isShaking(state.rawAccel)) {
            return AdaptationMode.COOL_DOWN_LOCK
        }

        // Prioridad 2: Modo lectura nocturna (Luz baja sostenida o lectura activa con poca luz)
        if (manager.isDarkStable(state.rawLux) || (isReading && state.rawLux < 15.0f)) {
            return AdaptationMode.NIGHT_READING
        }

        // Estado base por defecto
        return AdaptationMode.NORMAL
    }
}