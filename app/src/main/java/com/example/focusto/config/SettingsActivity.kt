package com.example.focusto.config

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.focusto.R
import kotlinx.coroutines.launch

/**
 * Pantalla de ajustes: perfil de duración del Pomodoro y qué apps bloquear durante el
 * estudio. Solo lee/escribe [FocusConfigRepository] — no conoce nada del Pomodoro en
 * ejecución. MainActivity recarga la configuración en su propio onResume() al volver,
 * así que los cambios se aplican solos sin que esta pantalla tenga que avisarle nada.
 */
class SettingsActivity : AppCompatActivity() {

    private val configRepository by lazy { FocusConfigRepository(applicationContext) }

    private lateinit var rgPomodoroProfile: RadioGroup
    private lateinit var rbProfileClassic: RadioButton
    private lateinit var rbProfileLong: RadioButton
    private lateinit var rbProfileShort: RadioButton

    private lateinit var cbTiktok: CheckBox
    private lateinit var cbInstagram: CheckBox
    private lateinit var cbFacebook: CheckBox
    private lateinit var cbWhatsapp: CheckBox
    private lateinit var cbTwitter: CheckBox
    private lateinit var cbSnapchat: CheckBox
    private lateinit var cbYoutube: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        initViews()
        loadCurrentConfig()
        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener { saveAndFinish() }
        findViewById<Button>(R.id.btnCancelSettings).setOnClickListener { finish() }
    }

    private fun initViews() {
        rgPomodoroProfile = findViewById(R.id.rgPomodoroProfile)
        rbProfileClassic = findViewById(R.id.rbProfileClassic)
        rbProfileLong = findViewById(R.id.rbProfileLong)
        rbProfileShort = findViewById(R.id.rbProfileShort)
        cbTiktok = findViewById<CheckBox>(R.id.cbTiktok).apply { text = BlockableApp.TIKTOK.label }
        cbInstagram = findViewById<CheckBox>(R.id.cbInstagram).apply { text = BlockableApp.INSTAGRAM.label }
        cbFacebook = findViewById<CheckBox>(R.id.cbFacebook).apply { text = BlockableApp.FACEBOOK.label }
        cbWhatsapp = findViewById<CheckBox>(R.id.cbWhatsapp).apply { text = BlockableApp.WHATSAPP.label }
        cbTwitter = findViewById<CheckBox>(R.id.cbTwitter).apply { text = BlockableApp.TWITTER.label }
        cbSnapchat = findViewById<CheckBox>(R.id.cbSnapchat).apply { text = BlockableApp.SNAPCHAT.label }
        cbYoutube = findViewById<CheckBox>(R.id.cbYoutube).apply { text = BlockableApp.YOUTUBE.label }
    }

    private fun loadCurrentConfig() {
        lifecycleScope.launch {
            val config = configRepository.currentConfig()

            when (PomodoroProfile.fromDurations(config.studyDurationMs, config.breakDurationMs)) {
                PomodoroProfile.CLASSIC -> rbProfileClassic.isChecked = true
                PomodoroProfile.LONG -> rbProfileLong.isChecked = true
                PomodoroProfile.SHORT -> rbProfileShort.isChecked = true
            }

            cbTiktok.isChecked = BlockableApp.TIKTOK.packageName in config.blockedApps
            cbInstagram.isChecked = BlockableApp.INSTAGRAM.packageName in config.blockedApps
            cbFacebook.isChecked = BlockableApp.FACEBOOK.packageName in config.blockedApps
            cbWhatsapp.isChecked = BlockableApp.WHATSAPP.packageName in config.blockedApps
            cbTwitter.isChecked = BlockableApp.TWITTER.packageName in config.blockedApps
            cbSnapchat.isChecked = BlockableApp.SNAPCHAT.packageName in config.blockedApps
            cbYoutube.isChecked = BlockableApp.YOUTUBE.packageName in config.blockedApps
        }
    }

    private fun saveAndFinish() {
        val profile = when (rgPomodoroProfile.checkedRadioButtonId) {
            R.id.rbProfileLong -> PomodoroProfile.LONG
            R.id.rbProfileShort -> PomodoroProfile.SHORT
            else -> PomodoroProfile.CLASSIC
        }

        val blockedApps = buildSet {
            if (cbTiktok.isChecked) add(BlockableApp.TIKTOK.packageName)
            if (cbInstagram.isChecked) add(BlockableApp.INSTAGRAM.packageName)
            if (cbFacebook.isChecked) add(BlockableApp.FACEBOOK.packageName)
            if (cbWhatsapp.isChecked) add(BlockableApp.WHATSAPP.packageName)
            if (cbTwitter.isChecked) add(BlockableApp.TWITTER.packageName)
            if (cbSnapchat.isChecked) add(BlockableApp.SNAPCHAT.packageName)
            if (cbYoutube.isChecked) add(BlockableApp.YOUTUBE.packageName)
        }

        lifecycleScope.launch {
            configRepository.updateDurations(profile.studyDurationMs, profile.breakDurationMs)
            configRepository.updateBlockedApps(blockedApps)
            finish()
        }
    }
}
