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
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    DataClient.OnDataChangedListener,
    MessageClient.OnMessageReceivedListener,
    CapabilityClient.OnCapabilityChangedListener {

    // === UI ===
    private lateinit var conectar: Button
    private lateinit var textinfo: TextView

    // === Variables ===
    private var activityContext: Context? = null
    private var deviceConnected: Boolean = false
    private var nodeID: String = ""
    private val PAYLOAD_PATH = "/APP_OPEN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // === Referencias de UI ===
        activityContext = this
        conectar = findViewById(R.id.boton)
        textinfo = findViewById(R.id.textinfo)

        // === Acción del botón ===
        conectar.setOnClickListener {
            if (!deviceConnected) {
                val tempAct: Activity = activityContext as MainActivity
                getNodes(tempAct)
            } else {
                textinfo.text = "✅ Dispositivo ya conectado"
            }
        }
    }

    // === FUNCIÓN DE PETICIÓN HTTP ===
    fun get(url: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: "Sin respuesta"

                Log.d("HTTP", "Respuesta del servidor: $body")

                withContext(Dispatchers.Main) {
                    textinfo.text = "🌐 Respuesta HTTP:\n$body"
                }
            } catch (e: Exception) {
                Log.e("HTTP", "Error en la petición: ${e.message}")
                withContext(Dispatchers.Main) {
                    textinfo.text = "⚠️ Error en la petición HTTP"
                }
            }
        }
    }

    // === OBTENER NODOS CONECTADOS (RELOJ) ===
    private fun getNodes(context: Context) {
        launch(Dispatchers.Default) {
            val nodeList = Wearable.getNodeClient(context).connectedNodes
            try {
                val nodes = Tasks.await(nodeList)
                for (node in nodes) {
                    Log.d("Nodo", "Encontrado: ${node.displayName} | ID: ${node.id}")
                    nodeID = node.id
                    deviceConnected = true
                }

                withContext(Dispatchers.Main) {
                    if (deviceConnected) {
                        textinfo.text = "🔗 Conectado al reloj: $nodeID"
                    } else {
                        textinfo.text = "❌ No se encontraron nodos conectados"
                    }
                }

            } catch (exception: Exception) {
                Log.e("ErrorNodo", exception.toString())
                withContext(Dispatchers.Main) {
                    textinfo.text = "⚠️ Error al obtener nodos"
                }
            }
        }
    }

    // === LISTENERS DE CICLO DE VIDA ===
    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(this).addListener(this)
            Wearable.getMessageClient(this).addListener(this)
            Wearable.getCapabilityClient(this)
                .addListener(this, Uri.parse("wear://"), CapabilityClient.FILTER_REACHABLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(this).removeListener(this)
            Wearable.getMessageClient(this).removeListener(this)
            Wearable.getCapabilityClient(this).removeListener(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // === EVENTOS DE WEAR OS ===
    override fun onDataChanged(p0: DataEventBuffer) {
        // Si en el futuro usas DataItems
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        Log.d("Capability", "Cambio detectado: ${info.name}")
    }

    override fun onMessageReceived(event: MessageEvent) {
        val message = String(event.data, StandardCharsets.UTF_8)
        Log.d("Mobile", "Mensaje recibido: $message")

        runOnUiThread {
            textinfo.text = "📡 Datos del reloj:\n$message"
        }

        // Ejemplo: si el mensaje contiene una URL, hacer petición
        if (message.contains("http")) {
            get(message)
        }
    }
}
