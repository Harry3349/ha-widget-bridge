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
import de.reimann.hawidget.wear.data.RowDef
import de.reimann.hawidget.wear.data.RowItem
import de.reimann.hawidget.wear.data.ValueState
import de.reimann.hawidget.wear.data.WidgetSnapshot
import de.reimann.hawidget.wear.icons.TileIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Baut aus einem Snapshot die Tile-Oberfläche (ProtoLayout). */
object TileRenderer {

    /** Kennung der klickbaren Buttons in der Tile. */
    const val PRESS_PREFIX = "press:"

    private const val LABEL_COLOR = 0xFF999999.toInt()
    private const val NOTE_COLOR = 0xFF888888.toInt()
    private const val BUTTON_BACKGROUND = 0x26FFFFFF
    private const val BUTTON_ACTIVE_BACKGROUND = 0x6600E676
    /** Ab wann ein Stand als veraltet gilt (Kommentar in der Zeitzeile). */
    private const val STALE_MS = 10 * 60 * 1000L
    private const val ACCENT = 0xFF00E676.toInt()

    fun render(
        context: Context,
        snapshot: WidgetSnapshot?,
        fetchedAt: Long,
        screenWidthDp: Int,
        note: String? = null,
    ): LayoutElementBuilders.LayoutElement {
        // Zeilen-Layout aus dem Handy-Editor hat Vorrang: gleiche Zeilen,
        // gleiche Ausrichtung – nur auf die runde Anzeige angepasst.
        val rows = snapshot?.rows.orEmpty().filter { it.items.isNotEmpty() }

        // Wird der Inhalt höher als die Anzeige, darf die Kachel nicht auf die
        // Bildschirmhöhe festgelegt werden (expand): dann schneidet die Uhr den
        // Rest ab. Mit wrap nimmt sie ihre natürliche Höhe ein und Wear OS legt
        // sie in einen Scrollbereich – Wischen und Krone scrollen.
        val scrollable = rows.isNotEmpty()

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())
            .setHeight(
                if (scrollable) DimensionBuilders.wrap() else DimensionBuilders.expand()
            )
            // Runde Displays schneiden oben und an den Seiten ab – ohne Abstand
            // verschwindet der Titel in der Rundung.
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(10f))
                            .setStart(DimensionBuilders.dp(8f))
                            .setEnd(DimensionBuilders.dp(8f))
                            .setBottom(if (scrollable) DimensionBuilders.dp(14f) else DimensionBuilders.dp(0f))
                            .build()
                    )
                    .build()
            )

        val name = snapshot?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.tile_not_configured)
        column.addContent(text(name, Color.WHITE, 12f))

        val noteText = when {
            !note.isNullOrBlank() -> note
            snapshot == null -> context.getString(R.string.tile_loading)
            !snapshot.error.isNullOrBlank() -> snapshot.error
            // Der Stand kommt vom Handy – wenn lange nichts kam, ist es nicht erreichbar
            fetchedAt > 0L && System.currentTimeMillis() - fetchedAt > STALE_MS ->
                context.getString(R.string.tile_stale, timeOf(fetchedAt))
            else -> null
        }
        column.addContent(
            text(
                // Zeitpunkt des letzten Abrufs – nicht der Änderungszeitpunkt der
                // Definition aus Home Assistant.
                noteText ?: context.getString(R.string.tile_updated, timeOf(fetchedAt)),
                if (noteText == null) NOTE_COLOR else ACCENT,
                9f,
                maxLines = 2,
            )
        )

        // Zeilen-Layout: gleiche Zeilen und Ausrichtungen wie am Handy.
        if (rows.isNotEmpty()) {
            // Links und rechts bleibt Platz für die Rundung: sonst schneidet
            // das Display die ersten Zeichen der äußeren Objekte ab.
            val inset = (screenWidthDp * 0.09f).coerceIn(14f, 24f)
            val rowArea = LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setStart(DimensionBuilders.dp(inset))
                                .setEnd(DimensionBuilders.dp(inset))
                                .build()
                        )
                        .build()
                )
            rows.forEach { row -> rowArea.addContent(rowLine(row)) }
            column.addContent(rowArea.build())
            return column.build()
        }

        // Buttons untereinander und alle gleich breit
        val buttons = snapshot?.buttons.orEmpty()
        val buttonWidth = (screenWidthDp - 48f).coerceAtLeast(80f)
        buttons.take(3).forEach { button -> column.addContent(button(button, buttonWidth)) }

        val values = snapshot?.values.orEmpty()
        if (values.isNotEmpty()) {
            val columns = snapshot?.valueColumns?.coerceIn(1, 3) ?: 1
            val stacked = snapshot?.valueLabelAbove == true
            if (columns > 1 && stacked) {
                // Raster mit dem Namen über dem Wert – wie das Werte-Raster am Handy
                val cell = ((screenWidthDp - 24f) / columns - 4f).coerceAtLeast(36f)
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

    /** Eine Zeile des Zeilen-Layouts: bis zu drei Objekte nebeneinander. */
    private fun rowLine(row: RowDef): LayoutElementBuilders.Row {
        val builder = LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.expand())
        row.items.take(3).forEach { item -> builder.addContent(rowCell(item)) }
        return builder.build()
    }

    /**
     * Ein Objekt in einer Zeile. Die Zelle nimmt den gleichen Anteil der Breite
     * wie die anderen Objekte der Zeile (``expand``), die Ausrichtung aus dem
     * Editor bestimmt die Position des Inhalts darin.
     */
    private fun rowCell(item: RowItem): LayoutElementBuilders.Box {
        val align = when (item.align.lowercase()) {
            "left" -> LayoutElementBuilders.HORIZONTAL_ALIGN_START
            "right" -> LayoutElementBuilders.HORIZONTAL_ALIGN_END
            else -> LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
        }
        val size = item.size.coerceIn(8f, 30f)
        val small = (size - 3f).coerceAtLeast(9f)

        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHorizontalAlignment(align)

        when (item.type) {
            "sensor" -> box.addContent(
                LayoutElementBuilders.Column.Builder()
                    .setHorizontalAlignment(align)
                    .addContent(text(item.label.orEmpty(), LABEL_COLOR, small))
                    .addContent(
                        text(item.text.orEmpty(), parseColor(item.color, Color.WHITE), size)
                    )
                    .build()
            )

            "button" -> {
                val key = item.key.orEmpty()
                val clickable = ModifiersBuilders.Clickable.Builder()
                    .setId(PRESS_PREFIX + key)
                    .setOnClick(ActionBuilders.LoadAction.Builder().build())
                    .build()
                val background = ModifiersBuilders.Background.Builder()
                    .setColor(
                        argb(
                            if (item.active) BUTTON_ACTIVE_BACKGROUND
                            else BUTTON_BACKGROUND
                        )
                    )
                    .build()

                box.setModifiers(
                    ModifiersBuilders.Modifiers.Builder()
                        .setClickable(clickable)
                        .setBackground(background)
                        .setPadding(
                            ModifiersBuilders.Padding.Builder()
                                .setTop(DimensionBuilders.dp(4f))
                                .setBottom(DimensionBuilders.dp(4f))
                                .setStart(DimensionBuilders.dp(4f))
                                .setEnd(DimensionBuilders.dp(4f))
                                .build()
                        )
                        .build()
                )

                val content = LayoutElementBuilders.Row.Builder()
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(TileIcons.id(item.icon))
                            .setWidth(DimensionBuilders.dp(14f))
                            .setHeight(DimensionBuilders.dp(14f))
                            .build()
                    )
                    .addContent(text(" " + item.label.orEmpty(), Color.WHITE, size))
                val state = item.stateLabel.orEmpty()
                if (state.isNotBlank()) {
                    content.addContent(
                        text(" " + state, if (item.active) ACCENT else LABEL_COLOR, small)
                    )
                }
                box.addContent(content.build())
            }

            else -> box.addContent(
                text(item.text.orEmpty(), parseColor(item.color, Color.WHITE), size)
            )
        }

        return box.build()
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
                            .addContent(text(value.label, LABEL_COLOR, 8f))
                            .addContent(
                                text(value.text, parseColor(value.color, Color.WHITE), 10f)
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
     * Button mit fester Breite – dadurch sind alle Buttons gleich breit, egal wie
     * lang die Beschriftung ist. Die Beschriftung wird darin zentriert.
     */
    private fun button(state: ButtonState, widthDp: Float): LayoutElementBuilders.Box {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(PRESS_PREFIX + state.key)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val background = ModifiersBuilders.Background.Builder()
            .setColor(
                argb(if (state.active) BUTTON_ACTIVE_BACKGROUND else BUTTON_BACKGROUND)
            )
            .build()

        val label = LayoutElementBuilders.Text.Builder()
            .setText(state.label)
            .setMaxLines(1)
            .setFontStyle(fontStyle(if (state.active) ACCENT else Color.WHITE, 12f))
            .build()

        val stateText = LayoutElementBuilders.Text.Builder()
            .setText("  " + state.stateLabel)
            .setMaxLines(1)
            .setFontStyle(fontStyle(if (state.active) ACCENT else LABEL_COLOR, 11f))
            .build()

        val icon = LayoutElementBuilders.Image.Builder()
            .setResourceId(TileIcons.id(state.icon))
            .setWidth(DimensionBuilders.dp(16f))
            .setHeight(DimensionBuilders.dp(16f))
            .build()

        val content = LayoutElementBuilders.Row.Builder()
            .addContent(icon)
            .addContent(label)
            .addContent(stateText)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(widthDp))
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(clickable)
                    .setBackground(background)
                    // Polsterung macht den Button größer und leichter zu treffen
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(4f))
                            .setBottom(DimensionBuilders.dp(4f))
                            .setStart(DimensionBuilders.dp(6f))
                            .setEnd(DimensionBuilders.dp(6f))
                            .build()
                    )
                    .build()
            )
            .addContent(
                LayoutElementBuilders.Column.Builder()
                    .setWidth(DimensionBuilders.expand())
                    .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                    .addContent(content)
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
