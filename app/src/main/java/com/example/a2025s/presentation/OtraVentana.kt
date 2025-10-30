package com.example.a2025s.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.wearable.Wearable
import com.example.a2025s.R

class OtraVentana : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var heartSensor: Sensor? = null
    private var lightSensor: Sensor? = null

    private lateinit var textoHeart: TextView
    private lateinit var textoLight: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.otra_ventana)

        textoHeart = findViewById(R.id.textoHeart)
        textoLight = findViewById(R.id.textoLight)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        heartSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        startSensors()
    }

    private fun startSensors() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BODY_SENSORS), 1001)
            return
        }

        heartSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            ?: run { textoHeart.text = "❌ Sensor de ritmo cardiaco no disponible" }

        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
            ?: run { textoLight.text = "❌ Sensor de luz no disponible" }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        when (event?.sensor?.type) {
            Sensor.TYPE_HEART_RATE -> {
                val heartRate = event.values[0].toInt()
                textoHeart.text = "💓 Ritmo cardiaco: $heartRate bpm"
                sendMessageToPhone("Ritmo cardiaco: $heartRate bpm")
            }
            Sensor.TYPE_LIGHT -> {
                val lightLevel = event.values[0].toInt()
                textoLight.text = "💡 Luz ambiental: $lightLevel lx"
                sendMessageToPhone("Luz ambiental: $lightLevel lx")
            }
        }
    }

    private fun sendMessageToPhone(message: String) {
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(this).sendMessage(node.id, "/SENSOR_DATA", message.toByteArray())
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onResume() {
        super.onResume()
        heartSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }
}
