package com.example.twincompanion

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Manda la pregunta de la amiga al backend (POST /ask_antonio) y devuelve
 * la respuesta de Claude (en la voz de Antonio).
 */
object AskRepository {

    // ⚠️ Misma URL del backend. En la misma WiFi: la IP LAN de tu PC.
    // Cuando deployees: "https://tu-backend.up.railway.app"
    const val BASE_URL = "https://twin-companion-be-production.up.railway.app"

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Registra el token FCM de este teléfono para recibir avisos proactivos. */
    fun registerDevice(token: String) {
        thread {
            try {
                val body = JSONObject().put("token", token).toString()
                val conn = (URL("$BASE_URL/register_device").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                conn.responseCode
                conn.disconnect()
            } catch (e: Exception) {
                // silencioso
            }
        }
    }

    /** onResult se llama SIEMPRE en el hilo principal. null = error. */
    fun ask(question: String, onResult: (String?) -> Unit) {
        thread {
            val answer = try {
                val body = JSONObject().put("question", question).toString()
                val conn = (URL("$BASE_URL/ask_antonio").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10000
                    readTimeout = 20000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }

                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.readText() ?: ""
                conn.disconnect()

                if (code in 200..299) JSONObject(text).optString("answer").ifBlank { null } else null
            } catch (e: Exception) {
                null
            }
            mainHandler.post { onResult(answer) }
        }
    }
}
