package com.example.focusto.config

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.focusConfigDataStore by preferencesDataStore(name = "focus_config")

/**
 * Fuente de verdad para la configuración de FocusTo. Persiste con DataStore, así que
 * sobrevive a cerrar la app (a diferencia de las constantes fijas que reemplaza).
 *
 * Hoy ninguna pantalla llama a los métodos `update*` todavía — no existe una UI de
 * ajustes — pero la lectura ([config], [currentConfig]) ya es real: si en el futuro
 * algo escribe acá, el resto de la app lo ve automáticamente sin cambiar nada más.
 */
class FocusConfigRepository(context: Context) {

    private val dataStore = context.applicationContext.focusConfigDataStore

    private object Keys {
        val STUDY_DURATION_MS = longPreferencesKey("study_duration_ms")
        val BREAK_DURATION_MS = longPreferencesKey("break_duration_ms")
        val LIGHT_THRESHOLD_LUX = floatPreferencesKey("light_threshold_lux")
        val SHAKE_THRESHOLD_ACCEL = floatPreferencesKey("shake_threshold_accel")
        val PROXIMITY_THRESHOLD = floatPreferencesKey("proximity_threshold")
        val POSTURE_THRESHOLD_ANGLE = floatPreferencesKey("posture_threshold_angle")
        val BLOCKED_APPS = stringSetPreferencesKey("blocked_apps")
    }

    /** Emite la configuración actual, y de nuevo cada vez que algo la actualice. */
    val config: Flow<FocusConfig> = dataStore.data.map { prefs ->
        val defaults = FocusConfig()
        FocusConfig(
            studyDurationMs = prefs[Keys.STUDY_DURATION_MS] ?: defaults.studyDurationMs,
            breakDurationMs = prefs[Keys.BREAK_DURATION_MS] ?: defaults.breakDurationMs,
            lightThresholdLux = prefs[Keys.LIGHT_THRESHOLD_LUX] ?: defaults.lightThresholdLux,
            shakeThresholdAccel = prefs[Keys.SHAKE_THRESHOLD_ACCEL] ?: defaults.shakeThresholdAccel,
            proximityThreshold = prefs[Keys.PROXIMITY_THRESHOLD] ?: defaults.proximityThreshold,
            postureThresholdAngle = prefs[Keys.POSTURE_THRESHOLD_ANGLE] ?: defaults.postureThresholdAngle,
            blockedApps = prefs[Keys.BLOCKED_APPS] ?: defaults.blockedApps
        )
    }

    /** Lectura puntual (una sola vez), útil al arrancar un componente. */
    suspend fun currentConfig(): FocusConfig = config.first()

    suspend fun updateBlockedApps(apps: Set<String>) {
        dataStore.edit { it[Keys.BLOCKED_APPS] = apps }
    }

    suspend fun updateDurations(studyDurationMs: Long, breakDurationMs: Long) {
        dataStore.edit {
            it[Keys.STUDY_DURATION_MS] = studyDurationMs
            it[Keys.BREAK_DURATION_MS] = breakDurationMs
        }
    }

    suspend fun updateSensorThresholds(
        lightThresholdLux: Float,
        shakeThresholdAccel: Float,
        proximityThreshold: Float,
        postureThresholdAngle: Float
    ) {
        dataStore.edit {
            it[Keys.LIGHT_THRESHOLD_LUX] = lightThresholdLux
            it[Keys.SHAKE_THRESHOLD_ACCEL] = shakeThresholdAccel
            it[Keys.PROXIMITY_THRESHOLD] = proximityThreshold
            it[Keys.POSTURE_THRESHOLD_ANGLE] = postureThresholdAngle
        }
    }
}
