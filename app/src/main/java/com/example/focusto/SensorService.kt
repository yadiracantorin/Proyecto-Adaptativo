package com.example.focusto

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

class SensorService(context: Context, private val onDataReceived: (Float, Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var currentLux: Float = 100f
    private var currentAcceleration: Float = 0f

    fun startListening() {
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
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
                Sensor.TYPE_ACCELEROMETER -> {
                    val x = it.values[0]
                    val y = it.values[1]
                    val z = it.values[2]

                    // Cálculo del módulo de aceleración restando la gravedad (aprox 9.81)
                    val rawAccel = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    currentAcceleration = kotlin.math.abs(rawAccel - SensorManager.GRAVITY_EARTH)
                }
            }
            onDataReceived(currentLux, currentAcceleration)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}