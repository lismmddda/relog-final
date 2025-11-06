package com.example.a2025s

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import okhttp3.*
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    private lateinit var botonConectar: Button
    private lateinit var botonEnviar: Button
    private lateinit var textInfo: TextView
    private lateinit var textDatos: TextView
    private lateinit var textDetalles: TextView

    private var activityContext: Context? = null
    private var deviceConnected = false
    private lateinit var nodeID: String
    private lateinit var nodeName: String
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        activityContext = this
        botonConectar = findViewById(R.id.boton)
        botonEnviar = findViewById(R.id.botonEnviar)
        textInfo = findViewById(R.id.textinfo)
        textDatos = findViewById(R.id.textDatos)
        textDetalles = findViewById(R.id.textDetalles)

        botonConectar.setOnClickListener {
            if (!deviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                getNodes(tempAct)
            } else {
                textInfo.text = "Ya estás conectado al reloj"
            }
        }

        botonEnviar.setOnClickListener {
            val datos = textDatos.text.toString().trim()
            if (datos.isNotBlank()) {
                textInfo.text = "Enviando datos al servidor..."
                sendSensorDataToServer(datos)
            } else {
                textInfo.text = "No hay datos válidos del reloj"
            }
        }
    }

    // === Buscar reloj conectado ===
    private fun getNodes(context: Context) {
        launch(Dispatchers.IO) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isNotEmpty()) {
                    val nodo = nodes.first()
                    nodeID = nodo.id
                    nodeName = nodo.displayName
                    deviceConnected = true

                    withContext(Dispatchers.Main) {
                        textInfo.text = "Conectado al reloj"
                        textDetalles.text = "Reloj: $nodeName\nID: $nodeID"
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        textInfo.text = "No se encontró ningún reloj conectado"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    textInfo.text = "Error al conectar con el reloj: ${e.message}"
                }
            }
        }
    }

    // === Recibir datos del reloj ===
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path == "/SENSOR_DATA") {
            val message = String(event.data, StandardCharsets.UTF_8)
            Log.d("Mobile", "Datos recibidos del reloj: $message")

            runOnUiThread {
                Log.d("RAW_DATA", "Mensaje recibido completo: $message")

                val datos = message.split("|").map { it.trim() }

                val builder = StringBuilder()
                builder.append("📡 Datos recibidos:\n\n")
                for (dato in datos) {
                    builder.append("• $dato\n")
                }

                textDatos.text = builder.toString()
                textInfo.text = "Datos recibidos correctamente"

                val horaActual = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                textDetalles.text = "Reloj: $nodeName\nÚltimo dato: $horaActual"
            }
        }
    }

    // === Enviar datos al servidor (URL Ngrok) ===
    private fun sendSensorDataToServer(sensorData: String) {
        try {
            Log.d("RAW_SENSOR", "Procesando datos: $sensorData")

            val limpio = sensorData
                .replace("bpm", "", ignoreCase = true)
                .replace("lx", "", ignoreCase = true)
                .replace("rad/s", "", ignoreCase = true)
                .replace("[^0-9.|:-]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()

            val numeros = Regex("""([0-9]+(?:\.[0-9]+)?)""").findAll(limpio).map { it.value }.toList()

            if (numeros.size >= 3) {
                val ritmo = numeros[0]
                val luz = numeros[1]
                val gyro = numeros[2]

                // URL Ngrok actual (HTTPS)
                val baseUrl = "https://bab40d7adbf4.ngrok-free.app/smartphone/guardar_datos.php"
                val url = "$baseUrl?ritmo=$ritmo&luz=$luz&gyro=$gyro"
                Log.d("HTTP", "Enviando a: $url")

                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val response = client.newCall(request).execute()
                        val body = response.body?.string() ?: "Sin respuesta"
                        Log.d("HTTP", "Respuesta del servidor: $body")

                        withContext(Dispatchers.Main) {
                            textInfo.text = "✅ Enviado al servidor:\n$body"
                        }
                    } catch (e: Exception) {
                        Log.e("HTTP", "Error HTTP: ${e.message}")
                        withContext(Dispatchers.Main) {
                            textInfo.text = "❌ Error al enviar datos: ${e.message}"
                        }
                    }
                }
            } else {
                textInfo.text = "No se pudieron leer los valores correctamente ($numeros)"
                Log.e("Parse", "Datos insuficientes: $numeros")
            }

        } catch (e: Exception) {
            textInfo.text = "Error procesando datos: ${e.message}"
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        Wearable.getMessageClient(this).addListener(this)
        Wearable.getCapabilityClient(this)
            .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getDataClient(this).removeListener(this)
        Wearable.getMessageClient(this).removeListener(this)
        Wearable.getCapabilityClient(this).removeListener(this)
    }

    override fun onDataChanged(p0: DataEventBuffer) {}
    override fun onCapabilityChanged(p0: CapabilityInfo) {}
}
