package com.example.focusto

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.acos
import kotlin.math.sqrt

class SensorService(context: Context, private val onDataReceived: (Float, Float, Float, Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    private var currentLux: Float = 100f
    private var currentAcceleration: Float = 0f
    private var currentProximity: Float = 5f
    private var currentInclination: Float = 0f

    fun startListening() {
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        proximitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            when (it.sensor.type) {
                Sensor.TYPE_LIGHT -> {
                    currentLux = it.values[0]
                }
                Sensor.TYPE_PROXIMITY -> {
                    currentProximity = it.values[0]
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]

                    // Magnitud total de la aceleración
                    val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    
                    // Cálculo del módulo de aceleración dinámica (restando gravedad)
                    currentAcceleration = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)

                    // Cálculo de inclinación respecto al plano horizontal (0 a 90 grados)
                    // acos(z / magnitude) nos da el ángulo respecto al eje Z (perpendicular a la pantalla)
                    // 0 rad = pantalla mirando al cielo (horizontal), PI/2 rad = vertical
                    val angleRad = acos((z / magnitude).toDouble())
                    currentInclination = Math.toDegrees(angleRad).toFloat()
                }
            }
            onDataReceived(currentLux, currentAcceleration, currentProximity, currentInclination)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}