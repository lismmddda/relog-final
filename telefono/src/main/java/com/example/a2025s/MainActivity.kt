package com.example.a2025s

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import kotlinx.coroutines.*
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity(),
    CoroutineScope by MainScope(),
    MessageClient.OnMessageReceivedListener {

    private lateinit var botonConectar: Button
    private lateinit var textInfo: TextView
    private lateinit var textDatos: TextView

    private var nodeID: String = ""
    private var nodeName: String = ""
    private var deviceConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        botonConectar = findViewById(R.id.boton)
        textInfo = findViewById(R.id.textinfo)
        textDatos = findViewById(R.id.textDatos)

        botonConectar.setOnClickListener {
            obtenerNodos(this)
        }
    }

    // 🔹 Buscar relojes conectados
    private fun obtenerNodos(context: Context) {
        launch(Dispatchers.IO) {
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                if (nodes.isNotEmpty()) {
                    val nodo = nodes.first()
                    nodeID = nodo.id
                    nodeName = nodo.displayName
                    deviceConnected = true
                    Log.d("Mobile", " Conectado al reloj: $nodeName ($nodeID)")
                } else {
                    deviceConnected = false
                }

                withContext(Dispatchers.Main) {
                    textInfo.text = if (deviceConnected)
                        " Conectado al reloj: $nodeName"
                    else
                        " No se encontraron relojes conectados"
                }
            } catch (e: Exception) {
                Log.e("Mobile", "💥 Error al obtener nodos: ${e.message}")
                withContext(Dispatchers.Main) {
                    textInfo.text = " Error al obtener nodos"
                }
            }
        }
    }


    override fun onMessageReceived(event: MessageEvent) {
        val message = String(event.data, StandardCharsets.UTF_8)

        // Asegúrate de usar la MISMA ruta que en el reloj
        if (event.path == "/SENSOR_DATA") {
            Log.d("Mobile", " Datos recibidos: $message")
            runOnUiThread {
                textDatos.text = message
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Wearable.getMessageClient(this).addListener(this)
    }

    override fun onPause() {
        super.onPause()
        Wearable.getMessageClient(this).removeListener(this)
    }
}
