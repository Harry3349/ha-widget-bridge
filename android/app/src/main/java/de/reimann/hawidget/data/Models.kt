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

/** Farben eines Widgets. */
data class WidgetTheme(
    val background: String = "#E6101018",
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
) {
    /**
     * Zeilen für die Editor-Vorschau: die Werte in der eingestellten
     * Spaltenzahl, damit die Anordnung schon vor dem Speichern sichtbar ist.
     */
    fun previewLines(): List<String> {
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
