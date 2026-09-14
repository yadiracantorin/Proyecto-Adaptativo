package com.example.focusto.config

/** Perfiles predefinidos de duración de Pomodoro que el usuario puede elegir en Ajustes. */
enum class PomodoroProfile(val label: String, val studyDurationMs: Long, val breakDurationMs: Long) {
    CLASSIC("Clásico (25 min / 5 min)", 25 * 60 * 1000L, 5 * 60 * 1000L),
    LONG("Largo (50 min / 10 min)", 50 * 60 * 1000L, 10 * 60 * 1000L),
    SHORT("Corto (15 min / 3 min)", 15 * 60 * 1000L, 3 * 60 * 1000L);

    companion object {
        /** Perfil que coincide exactamente con estas duraciones, o CLASSIC si ninguno coincide. */
        fun fromDurations(studyDurationMs: Long, breakDurationMs: Long): PomodoroProfile =
            entries.find { it.studyDurationMs == studyDurationMs && it.breakDurationMs == breakDurationMs }
                ?: CLASSIC
    }
}
