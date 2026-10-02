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
    /** „An“ oder „Aus“ – wie am Handy. */
    val stateLabel: String = "",
    /** ``mdi:``-Name des Symbols. */
    val icon: String? = null,
)

/** Ein Objekt innerhalb einer Zeile (Text, Sensor oder Button). */
data class RowItem(
    /** text | sensor | button */
    val type: String = "text",
    val text: String? = null,
    val entity: String? = null,
    val label: String? = null,
    val color: String? = null,
    /** left | center | right */
    val align: String = "center",
    val size: Float = 14f,
    // Button
    val key: String? = null,
    val icon: String? = null,
    val stateLabel: String? = null,
    val active: Boolean = false,
    val available: Boolean = true,
)

/** Eine Zeile mit bis zu drei Objekten. */
data class RowDef(
    val items: List<RowItem> = emptyList(),
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
    /** Zeilen-Layout aus dem Handy-Editor; leer = klassische Darstellung. */
    val rows: List<RowDef> = emptyList(),
) {
    val hasRows: Boolean get() = rows.any { it.items.isNotEmpty() }

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
