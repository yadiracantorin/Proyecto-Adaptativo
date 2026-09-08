package com.example.focusto

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Alerta de salud/postura mostrada en el banner no bloqueante. */
data class HealthAlert(val message: String, val isPositive: Boolean)

/**
 * Estado de UI de [MainActivity]. Se guarda como un único bloque inmutable para que
 * `MainActivity` pueda restaurarlo por completo tras recrearse (p. ej. al rotar la pantalla),
 * sin depender de que el usuario recuerde interactuar de nuevo con la pantalla.
 */
data class FocusUiState(
    val isPomodoroRunning: Boolean = false,
    val isBreakTime: Boolean = false,
    val currentSessionDuration: Long = FocusViewModel.STUDY_DURATION_MS,
    val timerText: String = "25:00",
    val startButtonText: String = "INICIAR",
    val pdfUriString: String? = null,
    val isPdfLoaded: Boolean = false,
    val currentPageIndex: Int = 0,
    val totalPages: Int = 0,
    val zoomLevel: Float = 2.0f,
    val isNightModeActive: Boolean = false,
    val currentLuxValue: Int = 0,
    val healthAlert: HealthAlert? = null,
    val lockMessage: String? = null,
    val physicalStudyModeNotified: Boolean = false
)

/**
 * Mantiene el estado de UI que hoy vive disperso en variables de [MainActivity].
 * Al sobrevivir a la recreación de la Activity (rotación de pantalla), evita que la UI
 * quede desincronizada del estado real (p. ej. el Pomodoro sigue corriendo en FocusService
 * pero la Activity recién creada mostraría "INICIAR").
 *
 * Deliberadamente NO conoce Views, Context ni Intents: solo expone estado y funciones
 * puras que lo transforman, para que sea testeable con JUnit sin Android framework.
 */
class FocusViewModel : ViewModel() {

    companion object {
        const val STUDY_DURATION_MS = 25 * 60 * 1000L
        const val BREAK_DURATION_MS = 5 * 60 * 1000L
    }

    private val _uiState = MutableStateFlow(FocusUiState())
    val uiState: StateFlow<FocusUiState> = _uiState.asStateFlow()

    /** Lectura síncrona del último estado, útil para decisiones puntuales fuera del collector. */
    val currentState: FocusUiState
        get() = _uiState.value

    fun onPomodoroStarted() {
        val duration = if (currentState.isBreakTime) BREAK_DURATION_MS else STUDY_DURATION_MS
        _uiState.value = currentState.copy(
            isPomodoroRunning = true,
            physicalStudyModeNotified = false,
            startButtonText = "PAUSAR",
            currentSessionDuration = duration
        )
    }

    fun onPomodoroPaused() {
        _uiState.value = currentState.copy(isPomodoroRunning = false, startButtonText = "CONTINUAR")
    }

    fun onPomodoroReset() {
        _uiState.value = currentState.copy(
            isPomodoroRunning = false,
            isBreakTime = false,
            startButtonText = "INICIAR",
            timerText = "25:00",
            currentSessionDuration = STUDY_DURATION_MS
        )
    }

    /** Terminó una sesión (`wasBreak = false`) o un descanso (`wasBreak = true`). */
    fun onPomodoroFinished(wasBreak: Boolean) {
        _uiState.value = if (!wasBreak) {
            currentState.copy(
                isBreakTime = true,
                isPomodoroRunning = true,
                startButtonText = "PAUSAR",
                currentSessionDuration = BREAK_DURATION_MS,
                timerText = "05:00"
            )
        } else {
            currentState.copy(
                isBreakTime = false,
                isPomodoroRunning = false,
                startButtonText = "INICIAR",
                timerText = "25:00",
                currentSessionDuration = STUDY_DURATION_MS
            )
        }
    }

    fun onTimerTick(timerText: String) {
        _uiState.value = currentState.copy(timerText = timerText)
    }

    fun onPdfOpened(uriString: String, totalPages: Int) {
        _uiState.value = currentState.copy(
            pdfUriString = uriString,
            isPdfLoaded = totalPages > 0,
            totalPages = totalPages,
            currentPageIndex = 0
        )
    }

    fun onPdfClosed() {
        _uiState.value = currentState.copy(
            pdfUriString = null,
            isPdfLoaded = false,
            totalPages = 0,
            currentPageIndex = 0
        )
    }

    fun onPageChanged(index: Int) {
        _uiState.value = currentState.copy(currentPageIndex = index)
    }

    fun onZoomChanged(zoom: Float) {
        _uiState.value = currentState.copy(zoomLevel = zoom)
    }

    fun onNightModeChanged(enabled: Boolean) {
        _uiState.value = currentState.copy(isNightModeActive = enabled)
    }

    fun onLuxUpdated(lux: Int) {
        _uiState.value = currentState.copy(currentLuxValue = lux)
    }

    fun setPhysicalStudyModeNotified(notified: Boolean) {
        _uiState.value = currentState.copy(physicalStudyModeNotified = notified)
    }

    fun showHealthAlert(message: String, isPositive: Boolean = false) {
        _uiState.value = currentState.copy(healthAlert = HealthAlert(message, isPositive))
    }

    fun hideHealthAlert() {
        _uiState.value = currentState.copy(healthAlert = null)
    }

    fun showLockOverlay(message: String) {
        _uiState.value = currentState.copy(lockMessage = message)
    }

    fun hideLockOverlay() {
        _uiState.value = currentState.copy(lockMessage = null)
    }
}
