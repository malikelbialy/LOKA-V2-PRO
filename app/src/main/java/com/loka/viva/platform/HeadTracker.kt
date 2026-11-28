package com.loka.viva.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread

class HeadTracker(
    context: Context,
    private val onRotation: (Float, Float, Float) -> Unit
) {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var thread: HandlerThread? = null
    private var listener: SensorEventListener? = null

    fun start() {
        if (rotationSensor == null) return
        thread = HandlerThread("VivaSensor").apply { start() }
        val handler = Handler(thread!!.looper)
        listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (e.sensor == rotationSensor) onRotation(e.values[0], e.values[1], e.values[2])
            }
            override fun onAccuracyChanged(s: Sensor, a: Int) {}
        }
        sm.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME, handler)
    }

    fun stop() {
        listener?.let { sm.unregisterListener(it) }
        listener = null
        thread?.quitSafely(); thread = null
    }
}
