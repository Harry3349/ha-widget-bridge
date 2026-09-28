package de.reimann.hawidget.data

import android.content.Context
import android.content.SharedPreferences

/**
 * App-Einstellungen (Server-URL, Token, Aktualisierungsintervall).
 *
 * Hinweis: Der Zugriffstoken liegt in normalen SharedPreferences. Die App ist
 * deshalb von Cloud-Backups ausgenommen (``allowBackup=false``) und der Token
 * sollte ein eigener, nur für diese App erzeugter Long-Lived-Token sein.
 */
class Settings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_URL, value.trim()).apply()
        }

    var token: String
        get() = prefs.getString(KEY_TOKEN, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_TOKEN, value.trim()).apply()
        }

    /** Aktualisierungsintervall in Minuten (WorkManager-Minimum: 15). */
    var refreshMinutes: Int
        get() = prefs.getInt(KEY_REFRESH, 15).coerceIn(MIN_REFRESH, MAX_REFRESH)
        set(value) {
            prefs.edit().putInt(KEY_REFRESH, value.coerceIn(MIN_REFRESH, MAX_REFRESH)).apply()
        }

    /** Live-Modus über WebSocket-Vordergrunddienst. */
    var liveMode: Boolean
        get() = prefs.getBoolean(KEY_LIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_LIVE, value).apply()
        }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    fun client(): HaClient = HaClient(baseUrl, token)

    companion object {
        private const val PREFS = "ha_widget_bridge_settings"
        private const val KEY_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_REFRESH = "refresh_minutes"
        private const val KEY_LIVE = "live_mode"

        const val MIN_REFRESH = 15
        const val MAX_REFRESH = 240
    }
}

/** Pro Homescreen-Widget: welche HA-Widget-ID ist damit verknüpft? */
object WidgetPrefs {

    private const val PREFS = "ha_widget_instances"
    private const val PREFIX_INSTANCE = "instance_"
    private const val PREFIX_SNAPSHOT = "snapshot_"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun widgetId(context: Context, appWidgetId: Int): String? =
        prefs(context).getString(PREFIX_INSTANCE + appWidgetId, null)

    fun setWidgetId(context: Context, appWidgetId: Int, widgetId: String) {
        prefs(context).edit().putString(PREFIX_INSTANCE + appWidgetId, widgetId).apply()
    }

    fun remove(context: Context, appWidgetId: Int) {
        prefs(context).edit().remove(PREFIX_INSTANCE + appWidgetId).apply()
    }

    fun instances(context: Context): Map<Int, String> {
        val result = LinkedHashMap<Int, String>()
        for ((key, value) in prefs(context).all) {
            if (!key.startsWith(PREFIX_INSTANCE)) continue
            val appWidgetId = key.removePrefix(PREFIX_INSTANCE).toIntOrNull() ?: continue
            (value as? String)?.let { result[appWidgetId] = it }
        }
        return result
    }

    /** Letzten erfolgreichen Snapshot merken, damit bei Netzproblemen nichts leer wird. */
    fun saveSnapshot(context: Context, widgetId: String, json: String) {
        prefs(context).edit().putString(PREFIX_SNAPSHOT + widgetId, json).apply()
    }

    fun loadSnapshot(context: Context, widgetId: String): String? =
        prefs(context).getString(PREFIX_SNAPSHOT + widgetId, null)
}
