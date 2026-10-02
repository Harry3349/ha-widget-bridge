package de.reimann.hawidget.wear.data

import android.util.Log
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Fehler beim Zugriff auf die HA-API. */
class HaApiException(message: String) : Exception(message)

/**
 * Zugriff auf das JSON-API der Integration ``ha_widget_bridge``.
 *
 * Alle Aufrufe sind blockierend und gehören in einen Hintergrund-Thread.
 */
class HaClient(baseUrl: String, private val token: String, timeoutSeconds: Long = 10) {

    val baseUrl: String = baseUrl.trim().trimEnd('/')

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun listWidgets(): String = execute(request("/api/ha_widget_bridge/widgets").get().build())

    /** Rohe Snapshot-Antwort – wird zwischengespeichert, damit ein Netzfehler die Anzeige nicht leert. */
    fun snapshotRaw(widgetId: String): String =
        execute(request("/api/ha_widget_bridge/widgets/$widgetId/snapshot").get().build())

    fun press(widgetId: String, buttonKey: String) {
        val json = org.json.JSONObject()
            .put("widget_id", widgetId)
            .put("button", buttonKey)
        val payload = json.toString().toRequestBody(jsonType)
        execute(request("/api/ha_widget_bridge/action").post(payload).build())
    }

    // -------------------------------------------------------------- intern

    private fun request(path: String): Request.Builder =
        Request.Builder()
            .url(baseUrl + path)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")

    private fun execute(request: Request): String {
        val path = request.url.encodedPath
        try {
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                Log.d(TAG, "${request.method} $path -> ${response.code}")
                if (!response.isSuccessful) {
                    throw HaApiException(describeError(response.code, path))
                }
                return body
            }
        } catch (error: HaApiException) {
            Log.w(TAG, "${request.method} $path fehlgeschlagen: ${error.message}")
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "${request.method} $path fehlgeschlagen: ${error.message}")
            throw HaApiException(error.message ?: "Verbindung fehlgeschlagen")
        }
    }

    private fun describeError(code: Int, path: String): String = when {
        code == 404 && path.startsWith("/api/ha_widget_bridge") ->
            "Die Integration HA Widget Bridge ist in Home Assistant nicht geladen (404)."
        code == 401 -> "Token abgelehnt (401) – neuen Long-Lived-Token erstellen."
        code == 502 || code == 503 -> "Home Assistant startet gerade oder ist nicht erreichbar ($code)."
        else -> "HTTP $code"
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
    }
}
