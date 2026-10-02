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
    /** 1–3 Spalten – dieselbe Einstellung wie im Handy-Editor. */
    val valueColumns: Int = 1,
    /** true = Name über dem Wert (wie am Handy). */
    val valueLabelAbove: Boolean = false,
) {
    companion object {
        val EMPTY = WidgetSnapshot("", "", 0, "", emptyList(), emptyList(), null)
    }
}

/**
 * Zwischenspeicher auf der Uhr.
 *
 * Die Uhr holt nichts selbst aus Home Assistant (meist kein WLAN): Den Snapshot
 * legt die Handy-App über den Wearable Data Layer hier ab.
 */
class Settings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("hawidget", Context.MODE_PRIVATE)

    /** Zuletzt empfangener Snapshot (JSON) aus Home Assistant. */
    var snapshotJson: String?
        get() = prefs.getString(KEY_SNAPSHOT, null)
        set(value) = prefs.edit().putString(KEY_SNAPSHOT, value).apply()

    /** Zeitpunkt des letzten empfangenen Snapshots. */
    var lastRefresh: Long
        get() = prefs.getLong(KEY_LAST_REFRESH, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_REFRESH, value).apply()

    /** Zeitpunkt der letzten Meldung „Tastendruck fehlgeschlagen“. */
    var pressFailedAt: Long
        get() = prefs.getLong(KEY_PRESS_FAILED, 0L)
        set(value) = prefs.edit().putLong(KEY_PRESS_FAILED, value).apply()

    companion object {
        private const val KEY_SNAPSHOT = "snapshot"
        private const val KEY_LAST_REFRESH = "last_refresh"
        private const val KEY_PRESS_FAILED = "press_failed_at"
    }
}
