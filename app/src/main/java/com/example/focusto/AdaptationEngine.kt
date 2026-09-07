package com.example.focusto

enum class AdaptationMode {
    NORMAL,
    NIGHT_READING,
    COOL_DOWN_LOCK,
    BAD_POSTURE,
    TOO_CLOSE,
    FACE_ABSENT
}

class AdaptationEngine {

    fun evaluateAdaptation(state: ContextState, isReading: Boolean, manager: ContextManager): AdaptationMode {
        // Prioridad 1: Detección de agitación violenta
        if (manager.isShaking(state.rawAccel)) {
            return AdaptationMode.COOL_DOWN_LOCK
        }

        // Prioridad 2: Ausencia (para pausa automática)
        if (manager.isFaceAbsent(state.rawInclination, state.rawAccel)) {
            return AdaptationMode.FACE_ABSENT
        }

        // Prioridad 3: Salud Visual (Distancia)
        if (manager.isTooClose(state.rawProximity)) {
            return AdaptationMode.TOO_CLOSE
        }

        // Prioridad 4: Ergonomía (Postura)
        if (manager.hasBadPosture(state.rawInclination)) {
            return AdaptationMode.BAD_POSTURE
        }

        // Prioridad 5: Modo nocturno
        if (manager.isDarkStable(state.rawLux) || (isReading && state.rawLux < 10.0f)) {
            return AdaptationMode.NIGHT_READING
        }

        return AdaptationMode.NORMAL
    }
}