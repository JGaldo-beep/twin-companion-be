package com.example.twincompanion.control

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Envía el estado de Antonio al backend FastAPI (POST /context).
 * El backend arma el display_text y lo escribe en Firestore;
 * el muñeco se entera en tiempo real.
 */
object ControlRepository {

    // ⚠️ CAMBIAR por la URL de tu backend.
    //  - Para probar en el mismo WiFi: la IP LAN de tu PC, ej "http://192.168.1.100:8000"
    //    (correr uvicorn con: uvicorn main:app --host 0.0.0.0 --port 8000)
    //  - Cuando lo deployees (Railway/Render): "https://tu-backend.up.railway.app"
    const val BASE_URL = "https://twin-companion-be-production.up.railway.app"

    data class Result(val ok: Boolean, val displayText: String?, val error: String?)

    fun postContext(
        status: String,
        emoji: String,
        message: String,
        availableAt: String? = null,
        locationCity: String? = null,
        calendar: String? = null
    ): Result {
        return try {
            val body = JSONObject().apply {
                put("status", status)
                put("emoji", emoji)
                put("message", message)
                put("available_at", availableAt)
                put("location_city", locationCity)
                put("calendar", calendar)
            }.toString()

            val conn = (URL("$BASE_URL/context").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
            }

            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.bufferedReader()?.readText() ?: ""
            conn.disconnect()

            if (code in 200..299) {
                val obj = JSONObject(response)
                val displayText = if (obj.has("display_text")) obj.getString("display_text") else null
                Result(true, displayText, null)
            } else {
                Result(false, null, "HTTP $code: $response")
            }
        } catch (e: Exception) {
            Result(false, null, e.message ?: "Error de red")
        }
    }

    // ── Memoria del clon ──────────────────────────────────────────

    fun getMemory(): List<String> = parseFacts(httpGet("/memory"))

    fun addMemory(fact: String): List<String> =
        parseFacts(httpPost("/memory", JSONObject().put("fact", fact).toString()))

    fun removeMemory(fact: String): List<String> =
        parseFacts(httpPost("/memory/remove", JSONObject().put("fact", fact).toString()))

    private fun parseFacts(json: String?): List<String> {
        if (json == null) return emptyList()
        return try {
            val arr = JSONObject(json).getJSONArray("facts")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun httpGet(path: String): String? {
        return try {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText()
            conn.disconnect()
            if (code in 200..299) text else null
        } catch (e: Exception) { null }
    }

    private fun httpPost(path: String, body: String): String? {
        return try {
            val conn = (URL("$BASE_URL$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; doOutput = true; connectTimeout = 8000; readTimeout = 8000
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText()
            conn.disconnect()
            if (code in 200..299) text else null
        } catch (e: Exception) { null }
    }

    /** Lee el contexto actual (GET /context). null si falla o no existe. */
    fun getContext(): JSONObject? {
        return try {
            val conn = (URL("$BASE_URL/context").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.readText() ?: ""
            conn.disconnect()
            if (code in 200..299) JSONObject(response) else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Refresca las fuentes automaticas (calendario, ubicacion), conservando el
     * estado/mensaje manual actual. Hace GET del contexto vigente y reenvia todo,
     * pisando solo los campos automaticos que se pasen (los null se conservan).
     */
    fun syncAuto(calendar: String? = null, locationCity: String? = null): Result {
        val current = getContext()
        return postContext(
            status = current?.optString("status", "available") ?: "available",
            emoji = current?.optString("emoji", "") ?: "",
            message = current?.optString("message", "") ?: "",
            availableAt = current?.optStringOrNull("available_at"),
            locationCity = locationCity ?: current?.optStringOrNull("location_city"),
            calendar = calendar ?: current?.optStringOrNull("calendar")
        )
    }

    /** Fija solo la actividad/mensaje (ej "haciendo arroz con leche"), conservando lo demas. */
    fun setActivity(message: String): Result {
        val current = getContext()
        return postContext(
            status = current?.optString("status", "available") ?: "available",
            emoji = current?.optString("emoji", "") ?: "",
            message = message,
            availableAt = current?.optStringOrNull("available_at"),
            locationCity = current?.optStringOrNull("location_city"),
            calendar = current?.optStringOrNull("calendar")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val v = optString(key, "")
        return v.ifBlank { null }
    }
}
