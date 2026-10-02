package de.reimann.hawidget.wear.tile

import android.content.Context
import android.graphics.Color
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import de.reimann.hawidget.wear.R
import de.reimann.hawidget.wear.data.ButtonState
import de.reimann.hawidget.wear.data.ValueState
import de.reimann.hawidget.wear.data.WidgetSnapshot
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Baut aus einem Snapshot die Tile-Oberfläche (ProtoLayout). */
object TileRenderer {

    /** Kennung der klickbaren Buttons in der Tile. */
    const val PRESS_PREFIX = "press:"

    private const val LABEL_COLOR = 0xFF999999.toInt()
    private const val NOTE_COLOR = 0xFF888888.toInt()
    private const val BUTTON_BACKGROUND = 0x26FFFFFF
    private const val BUTTON_ACTIVE_BACKGROUND = 0x6600E676
    private const val ACCENT = 0xFF00E676.toInt()

    fun render(
        context: Context,
        snapshot: WidgetSnapshot?,
        fetchedAt: Long,
        screenWidthDp: Int,
        note: String? = null,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            // Runde Displays schneiden oben und an den Seiten ab – ohne Abstand
            // verschwindet der Titel in der Rundung.
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(14f))
                            .setStart(DimensionBuilders.dp(10f))
                            .setEnd(DimensionBuilders.dp(10f))
                            .build()
                    )
                    .build()
            )

        val name = snapshot?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.tile_not_configured)
        column.addContent(text(name, Color.WHITE, 13f))

        val noteText = when {
            !note.isNullOrBlank() -> note
            snapshot == null -> context.getString(R.string.tile_loading)
            !snapshot.error.isNullOrBlank() -> snapshot.error
            else -> null
        }
        column.addContent(
            text(
                // Zeitpunkt des letzten Abrufs – nicht der Änderungszeitpunkt der
                // Definition aus Home Assistant.
                noteText ?: context.getString(R.string.tile_updated, timeOf(fetchedAt)),
                if (noteText == null) NOTE_COLOR else ACCENT,
                10f,
                maxLines = 2,
            )
        )

        // Wie am Handy: erst die Buttons, dann die Werte
        val buttons = snapshot?.buttons.orEmpty()
        if (buttons.isNotEmpty()) {
            val row = LayoutElementBuilders.Row.Builder()
            buttons.take(3).forEach { button -> row.addContent(button(button)) }
            column.addContent(row.build())
        }

        val values = snapshot?.values.orEmpty()
        if (values.isNotEmpty()) {
            val columns = snapshot?.valueColumns?.coerceIn(1, 3) ?: 1
            val stacked = snapshot?.valueLabelAbove == true
            if (columns > 1 && stacked) {
                // Raster mit dem Namen über dem Wert – wie das Werte-Raster am Handy
                val cell = ((screenWidthDp - 24) / columns - 4).coerceAtLeast(36)
                values.chunked(columns).forEach { chunk ->
                    column.addContent(gridRow(chunk, columns, cell))
                }
            } else {
                values.forEach { value ->
                    column.addContent(
                        LayoutElementBuilders.Row.Builder()
                            .addContent(text(value.label, LABEL_COLOR, 11f))
                            .addContent(text(" ", LABEL_COLOR, 11f))
                            .addContent(
                                text(value.text, parseColor(value.color, Color.WHITE), 11f)
                            )
                            .build()
                    )
                }
            }
        }

        return column.build()
    }

    /** Eine Rasterzeile: je Wert eine Zelle mit Name über dem Wert. */
    private fun gridRow(
        values: List<ValueState>,
        columns: Int,
        cellWidthDp: Float,
    ): LayoutElementBuilders.Row {
        val row = LayoutElementBuilders.Row.Builder()
        values.forEach { value ->
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.dp(cellWidthDp))
                    .addContent(
                        LayoutElementBuilders.Column.Builder()
                            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                            .addContent(text(value.label, LABEL_COLOR, 9f))
                            .addContent(
                                text(value.text, parseColor(value.color, Color.WHITE), 11f)
                            )
                            .build()
                    )
                    .build()
            )
        }
        // Leere Zellen auffüllen, damit die Spalten ausgerichtet bleiben
        repeat(columns - values.size) {
            row.addContent(
                LayoutElementBuilders.Box.Builder()
                    .setWidth(DimensionBuilders.dp(cellWidthDp))
                    .build()
            )
        }
        return row.build()
    }

    // ---------------------------------------------------------------- Bausteine

    /** Text mit Farbe und Größe – die Farbe gehört in den FontStyle. */
    private fun text(
        value: String,
        color: Int,
        sizeSp: Float,
        maxLines: Int = 1,
    ): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setMaxLines(maxLines)
            .setFontStyle(fontStyle(color, sizeSp))
            .build()

    private fun fontStyle(color: Int, sizeSp: Float): LayoutElementBuilders.FontStyle =
        LayoutElementBuilders.FontStyle.Builder()
            .setSize(DimensionBuilders.sp(sizeSp))
            .setColor(argb(color))
            .build()

    /** Farbwert als ``ColorProp`` – die statische Farbe steckt im Konstruktor. */
    private fun argb(value: Int): ColorBuilders.ColorProp =
        ColorBuilders.ColorProp.Builder(value).build()

    /**
     * Button als klickbarer Text mit Hintergrund.
     *
     * Bewusst kein ``Box``-Element: Ein Text mit Klick-Modifier und Hintergrund
     * verhält sich wie ein Button und braucht keine zusätzlichen Größenangaben.
     */
    private fun button(state: ButtonState): LayoutElementBuilders.Text {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(PRESS_PREFIX + state.key)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val background = ModifiersBuilders.Background.Builder()
            .setColor(
                argb(if (state.active) BUTTON_ACTIVE_BACKGROUND else BUTTON_BACKGROUND)
            )
            .build()

        return LayoutElementBuilders.Text.Builder()
            .setText(" ${state.label} ")
            .setFontStyle(fontStyle(if (state.active) ACCENT else Color.WHITE, 10f))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(background)
                    .build()
            )
            .build()
    }

    // ------------------------------------------------------------------ Helfer

    /** ``#AARRGGBB`` oder ``#RRGGBB`` lesen. */
    private fun parseColor(value: String?, fallback: Int): Int {
        val raw = value?.trim().orEmpty()
        if (!raw.startsWith("#")) return fallback
        val hex = raw.removePrefix("#")
        return runCatching {
            when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> fallback
            }
        }.getOrDefault(fallback)
    }

    /** Zeitpunkt des letzten Abrufs als ``HH:mm`` (lokale Uhrzeit). */
    private fun timeOf(millis: Long): String {
        if (millis <= 0L) return "–"
        return runCatching {
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("–")
    }
}
