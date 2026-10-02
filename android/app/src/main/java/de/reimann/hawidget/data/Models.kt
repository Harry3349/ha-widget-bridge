package de.reimann.hawidget.data

/** Ein angezeigter Wert innerhalb eines Widgets. */
data class WidgetValue(
    val entity: String,
    val label: String? = null,
    val threshold: Double? = null,
    val color: String? = null,
)

/** Ein Button innerhalb eines Widgets. */
data class WidgetButton(
    val key: String,
    val label: String,
    val icon: String? = null,
    val service: String = "switch.toggle",
    val entityId: String? = null,
    val stateEntity: String? = null,
    val stateLabelOn: String? = null,
    val stateLabelOff: String? = null,
)

/**
 * Ein Objekt innerhalb einer Zeile.
 *
 * In der Definition stehen die Einstellungen (Text, Entity, Ausrichtung,
 * Schriftgröße …), im Snapshot zusätzlich die fertig gerenderten Werte
 * (`text`, `stateLabel`, `active`, `available`).
 */
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
    val threshold: Double? = null,
    // Button
    val key: String? = null,
    val icon: String? = null,
    val service: String = "switch.toggle",
    val entityId: String? = null,
    val stateEntity: String? = null,
    val stateLabelOn: String? = null,
    val stateLabelOff: String? = null,
    // gerendert (nur im Snapshot)
    val stateLabel: String? = null,
    val active: Boolean = false,
    val available: Boolean = true,
) {
    /** Kurzbeschreibung für die Editor-Liste. */
    val summary: String
        get() = when (type) {
            "sensor" -> "Sensor · ${label ?: entity.orEmpty()}"
            "button" -> "Button · ${label ?: key.orEmpty()}"
            else -> "Text · ${text.orEmpty()}"
        }
}

/** Eine Zeile mit bis zu drei Objekten (Text, Sensor, Button). */
data class RowDef(
    /** false = diese Zeile nur am Handy zeigen, nicht auf der Uhr. */
    val watch: Boolean = true,
    val items: List<RowItem> = emptyList(),
)

/** Farben eines Widgets. */
data class WidgetTheme(
    val background: String = "#00000000",
    val textColor: String = "#FFFFFFFF",
    val accent: String = "#FF00E676",
    val buttonBackground: String = "#26FFFFFF",
    val buttonText: String = "#FFFFFFFF",
)

/** Vollständige Widget-Definition – wird in Home Assistant gespeichert. */
data class WidgetDef(
    val id: String,
    val name: String,
    val template: String? = null,
    val values: List<WidgetValue> = emptyList(),
    val buttons: List<WidgetButton> = emptyList(),
    /** Freies Zeilen-Layout für Handy-Widget und Uhr-Tile. */
    val rows: List<RowDef> = emptyList(),
    /** Zeilen auf der Uhr-Kachel; 0 = so viele, wie hineinpassen. */
    val watchRows: Int = 0,
    /** Schriftgrößen-Faktor für die Uhr (1.0 = wie eingestellt). */
    val watchScale: Float = 1f,
    val theme: WidgetTheme = WidgetTheme(),
    val textSize: Float = 14f,
    /** 1 = Werte untereinander, 2 oder 3 = nebeneinander */
    val valueColumns: Int = 1,
    /** true = Name in der ersten Zeile, Wert darunter */
    val valueLabelAbove: Boolean = false,
    val revision: Int = 0,
) {
    val isNew: Boolean get() = revision == 0
}

/** Zustand eines Buttons, wie ihn Home Assistant meldet. */
data class ButtonState(
    val key: String,
    val label: String,
    val icon: String?,
    val stateLabel: String,
    val active: Boolean,
    val available: Boolean,
)

/** Fertig gerenderter Inhalt eines Widgets. */
data class WidgetSnapshot(
    val id: String,
    val name: String,
    val revision: Int,
    val textSize: Float,
    val theme: WidgetTheme,
    val html: String,
    val text: String,
    val error: String?,
    val buttons: List<ButtonState>,
    /** true = Inhalt kommt aus dem Jinja-Template (dann keine Werte-Felder) */
    val templateUsed: Boolean = false,
    val valueColumns: Int = 1,
    val valueLabelAbove: Boolean = false,
    val values: List<ValueState> = emptyList(),
    /** Zeilen-Layout; leer = klassische Darstellung aus Werten/Buttons. */
    val rows: List<RowDef> = emptyList(),
    /** Zeilen auf der Uhr-Kachel; 0 = so viele, wie hineinpassen. */
    val watchRows: Int = 0,
    /** Schriftgrößen-Faktor für die Uhr (1.0 = wie eingestellt). */
    val watchScale: Float = 1f,
) {

    val hasRows: Boolean get() = rows.any { it.items.isNotEmpty() }
    /**
     * Zeilen für die Editor-Vorschau: die Werte in der eingestellten
     * Spaltenzahl, damit die Anordnung schon vor dem Speichern sichtbar ist.
     * Ist ein Zeilen-Layout gesetzt, wird dieses gezeigt.
     */
    fun previewLines(): List<String> {
        if (hasRows) {
            return rows.filter { it.items.isNotEmpty() }.map { row ->
                row.items.joinToString("    ") { item ->
                    val text = when (item.type) {
                        "sensor" -> listOfNotNull(item.label, item.text).joinToString(" ")
                        "button" -> listOfNotNull(item.label, item.stateLabel).joinToString(" ")
                        else -> item.text.orEmpty()
                    }
                    "$text [${item.align} ${item.size.toInt()}sp]"
                }
            }
        }
        if (values.isEmpty()) return emptyList()
        val columns = valueColumns.coerceIn(1, 3)
        return values.chunked(columns).map { row ->
            row.joinToString("    ") { value ->
                if (valueLabelAbove) "${value.label}\n${value.text}"
                else "${value.label} · ${value.text}"
            }
        }
    }
}

/** Ein aufbereiteter Wert, wie ihn Home Assistant für das Widget liefert. */
data class ValueState(
    val entity: String,
    val label: String,
    val text: String,
    val color: String,
    val active: Boolean,
    val available: Boolean,
)

/** Ein Entity für die Auswahlliste in der App. */
data class HaEntity(
    val entityId: String,
    val name: String,
    val state: String,
    val unit: String?,
    val icon: String?,
) {
    val display: String
        get() = if (unit.isNullOrBlank()) "$name · $state" else "$name · $state $unit"
}
