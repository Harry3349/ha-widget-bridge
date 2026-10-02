package de.reimann.hawidget.wear.data

import android.content.Context

/** Ein Wert, wie ihn Home Assistant liefert. */
data class ValueState(
    val entity: String,
    val label: String,
    val text: String,
    val color: String,
    val active: Boolean,
    val available: Boolean,
)

/** Zustand eines Buttons, wie ihn Home Assistant liefert. */
data class ButtonState(
    val key: String,
    val label: String,
    val active: Boolean,
    val available: Boolean,
)

/** Fertig gerenderter Inhalt eines Widgets (Snapshot der Integration). */
data class WidgetSnapshot(
    val id: String,
    val name: String,
    val revision: Int,
    val updatedAt: String,
    val values: List<ValueState>,
    val buttons: List<ButtonState>,
    val error: String?,
) {
    companion object {
        val EMPTY = WidgetSnapshot("", "", 0, "", emptyList(), emptyList(), null)
    }
}

/** Einstellungen und Zwischenspeicher auf der Uhr. */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hawidget", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_URL, value.trim().trimEnd('/')).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var widgetId: String
        get() = prefs.getString(KEY_WIDGET, "") ?: ""
        set(value) = prefs.edit().putString(KEY_WIDGET, value.trim()).apply()

    var snapshotJson: String?
        get() = prefs.getString(KEY_SNAPSHOT, null)
        set(value) = prefs.edit().putString(KEY_SNAPSHOT, value).apply()

    var lastRefresh: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REFRESH, value).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    companion object {
        private const val KEY_URL = "base_url"
        private const val KEY_TOKEN = "token"
        private const val KEY_WIDGET = "widget_id"
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_LAST_REFRESH = "last_refresh"
    }
}
