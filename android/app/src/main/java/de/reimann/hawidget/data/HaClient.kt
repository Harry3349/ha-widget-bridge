package de.reimann.hawidget.data

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
 * Alle Aufrufe sind blockierend und gehören daher in einen Hintergrund-Thread
 * (Coroutine auf ``Dispatchers.IO`` bzw. in einen Worker).
 */
class HaClient(baseUrl: String, private val token: String) {

    val baseUrl: String = normalizeBaseUrl(baseUrl)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    // ------------------------------------------------------------ Endpunkte

    /** Erreichbarkeit und Token prüfen. Liefert die API-Meldung. */
    fun testConnection(): String {
        val body = execute(request("/api/").get().build())
        return WidgetJson.errorOf(body) ?: "API erreichbar"
    }

    fun listWidgets(): List<WidgetDef> = WidgetJson.parseWidgets(execute(request("/api/ha_widget_bridge/widgets").get().build()))

    fun saveWidget(def: WidgetDef): WidgetDef {
        val payload = WidgetJson.toJson(def).toString().toRequestBody(jsonType)
        val body = execute(request("/api/ha_widget_bridge/widgets").post(payload).build())
        val json = org.json.JSONObject(body).optJSONObject("widget")
            ?: throw HaApiException("Unerwartete Antwort beim Speichern")
        return WidgetJson.parseWidget(json)
    }

    fun deleteWidget(widgetId: String) {
        execute(request("/api/ha_widget_bridge/widgets/$widgetId").delete().build())
    }

    fun snapshot(widgetId: String): WidgetSnapshot {
        return WidgetJson.parseSnapshot(snapshotRaw(widgetId))
    }

    /** Rohe Snapshot-Antwort – wird zwischengespeichert, damit ein Netzfehler die Anzeige nicht leert. */
    fun snapshotRaw(widgetId: String): String {
        return execute(request("/api/ha_widget_bridge/widgets/$widgetId/snapshot").get().build())
    }

    /** Definition rendern lassen, ohne sie zu speichern (Editor-Vorschau). */
    fun preview(def: WidgetDef): WidgetSnapshot {
        val payload = WidgetJson.toJson(def).toString().toRequestBody(jsonType)
        val body = execute(request("/api/ha_widget_bridge/preview").post(payload).build())
        return WidgetJson.parseSnapshot(body)
    }

    fun press(widgetId: String, buttonKey: String) {
        val json = org.json.JSONObject()
            .put("widget_id", widgetId)
            .put("button", buttonKey)
        val payload = json.toString().toRequestBody(jsonType)
        execute(request("/api/ha_widget_bridge/action").post(payload).build())
    }

    fun entities(): List<HaEntity> = WidgetJson.parseEntities(execute(request("/api/states").get().build()))

    /** WebSocket-URL für den Live-Modus. */
    fun websocketUrl(): String {
        val wsBase = when {
            baseUrl.startsWith("https://") -> "wss://" + baseUrl.removePrefix("https://")
            baseUrl.startsWith("http://") -> "ws://" + baseUrl.removePrefix("http://")
            else -> "wss://$baseUrl"
        }
        return "$wsBase/api/websocket"
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
                    throw HaApiException(describeError(response.code, path, body))
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

    /** Fehlercodes in hilfreiche Meldungen übersetzen. */
    private fun describeError(code: Int, path: String, body: String): String = when {
        code == 404 && path.startsWith("/api/ha_widget_bridge") ->
            "Die Integration „HA Widget Bridge\u201c ist in Home Assistant nicht geladen. " +
                "Bitte dort unter Einstellungen → Geräte & Dienste → Integration hinzufügen " +
                "einrichten und Home Assistant neu starten."

        code == 401 || code == 403 ->
            "Zugriff verweigert – bitte den Long-Lived Access Token in der Einrichtung prüfen."

        else -> WidgetJson.errorOf(body) ?: "HTTP $code"
    }

    companion object {
        private const val TAG = "HAWidgetBridge"

        /** ``homeassistant.local:8123`` → ``https://homeassistant.local:8123``. */
        fun normalizeBaseUrl(raw: String): String {
            var value = raw.trim().trimEnd('/')
            if (value.isEmpty()) return value
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                value = "https://$value"
            }
            return value
        }
    }
}
