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

    /** Kennung des Klicks, der die App-Ansicht öffnet. */
    const val OPEN_PREFIX = "open:"

    private const val APP_ACTIVITY = "de.reimann.hawidget.wear.app.WearAppActivity"

    /** Höhe von Titel und Zeile „Stand …“ mit Polsterung. */
    private const val HEADER_DP = 34f

    /** Platz für die Hinweiszeile („Antippen …“). */
    private const val HINT_DP = 12f

    /**
     * Unterer Rand der runden Anzeige – dort ist kein Platz mehr für Text.
     *
     * Bewusst knapp: eine zu große Reserve kostet Zeilen, die eigentlich noch
     * passen würden (z. B. Temperatur und Feuchte unter den Buttons).
     */
    private const val BOTTOM_SAFE_DP = 30f

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
        screenHeightDp: Int = 192,
        note: String? = null,
    ): LayoutElementBuilders.LayoutElement {
        // Zeilen-Layout aus dem Handy-Editor hat Vorrang: gleiche Zeilen,
        // gleiche Ausrichtung – nur auf die runde Anzeige angepasst. Welche
        // Zeilen auf die Uhr kommen, entscheidet der Editor am Handy.
        val rows = limitedRows(snapshot)
        // Schriftgröße so, wie sie im Editor eingestellt ist – die Kachel
        // verkleinert nichts von sich aus.
        val scale = snapshot?.watchScale ?: 1f
        // Kacheln können laut Wear OS nicht scrollen. Deshalb wird nur gezeigt, was
        // ganz auf die Anzeige passt; für den Rest weist ein Hinweis auf die
        // App-Ansicht hin.
        val plan = layoutPlan(snapshot, screenHeightDp)
        val truncated = plan.truncated
        val shown = rows.take(plan.shown)

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    // Tipp irgendwo auf die Kachel = volle Liste
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(OPEN_PREFIX)
                            .setOnClick(openAppAction(context))
                            .build()
                    )
                    // Runde Displays schneiden oben und an den Seiten ab – ohne Abstand
                    // verschwindet der Titel in der Rundung.
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setTop(DimensionBuilders.dp(10f))
                            .setStart(DimensionBuilders.dp(8f))
                            .setEnd(DimensionBuilders.dp(8f))
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
            if (truncated) {
                // Sichtbarer Hinweis: der Rest liegt in der App-Ansicht
                column.addContent(
                    text(context.getString(R.string.tile_more_rows), ACCENT, 9f)
                )
            }
            // Links und rechts bleibt Platz für die Rundung: sonst schneidet
            // das Display die ersten Zeichen der äußeren Objekte ab.
            val inset = (screenWidthDp * 0.10f).coerceIn(16f, 24f)
            // Bezug für Prozent-Breiten: die tatsächlich nutzbare Zeilenbreite
            val rowWidth = (screenWidthDp - 2f * inset).coerceAtLeast(60f)
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
            shown.forEach { row ->
                rowArea.addContent(rowLine(row, scale, rowWidth))
            }
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

    /**
     * Ein Tipp auf die Kachel öffnet die App-Ansicht: nur dort lässt sich der
     * Inhalt scrollen (Wischen und Krone).
     */
    private fun openAppAction(context: Context): ActionBuilders.Action =
        ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(
                ActionBuilders.AndroidActivity.Builder()
                    .setPackageName(context.packageName)
                    .setClassName(APP_ACTIVITY)
                    .build()
            )
            .build()

    /**
     * Höhe einer Zeile in dp – gemessen an der gezeichneten Kachel.
     *
     * Bewusst knapp gerechnet (früher 34/30 dp): zu große Werte ließen die Kachel
     * die unteren Zeilen weglassen, obwohl noch Platz war.
     */
    private fun rowHeightDp(row: RowDef, scale: Float): Float =
        row.items.maxOfOrNull { item ->
            when (item.type) {
                // Button: Symbol 14 dp + Polsterung; Sensor: eine Textzeile,
                // mit „Wert unter dem Namen“ zwei Zeilen
                "button" -> 20f * scale
                "sensor" ->
                    if (item.labelAbove && !item.label.isNullOrBlank()) {
                        15f * scale + (item.size * scale - 3f).coerceAtLeast(9f) * 1.3f
                    } else {
                        15f * scale
                    }
                else -> (item.size * scale).coerceIn(8f, 30f) * 1.3f + 4f
            }
        } ?: 0f

    /**
     * Was die Kachel aus einem Snapshot zeichnet.
     *
     * Auch für das Log im Tile-Dienst: damit lässt sich ohne Rätselraten sehen,
     * welchen Stand die Kachel bekommen hat und warum sie Zeilen weglässt.
     */
    data class TilePlan(
        /** Zeilen, die laut Editor auf die Uhr gehören. */
        val total: Int,
        /** Zeilen nach der Einstellung „Zeilen auf der Kachel“ (0 = alle). */
        val allowed: Int,
        /** Wie viele davon ohne Hinweiszeile passen würden. */
        val visible: Int,
        /** Wie viele die Kachel tatsächlich zeichnet. */
        val shown: Int,
        /** Ob Zeilen wegfallen (dann erscheint der Hinweis). */
        val truncated: Boolean,
        val scale: Float,
    )

    /** Die Zeilen, die laut Editor auf die Uhr gehören (Zeilenzahl beachtet). */
    private fun limitedRows(snapshot: WidgetSnapshot?): List<RowDef> {
        val all = snapshot?.watchRowsList.orEmpty()
        val limit = snapshot?.watchRows ?: 0
        return if (limit > 0) all.take(limit) else all
    }

    /** Rechnet aus, wie viele Zeilen die Kachel zeigt (ohne zu zeichnen). */
    fun layoutPlan(snapshot: WidgetSnapshot?, screenHeightDp: Int): TilePlan {
        val all = snapshot?.watchRowsList.orEmpty()
        val rows = limitedRows(snapshot)
        val scale = snapshot?.watchScale ?: 1f
        val band = screenHeightDp - HEADER_DP - BOTTOM_SAFE_DP
        val visible = fittingRows(rows, band, scale)
        val truncated = all.size > visible || rows.size > visible
        val shown = if (!truncated) {
            rows.size
        } else {
            // eine Zeile Platz für den Hinweis lassen
            rows.take(fittingRows(rows, band - HINT_DP, scale).coerceAtLeast(1)).size
        }
        return TilePlan(all.size, rows.size, visible, shown, truncated, scale)
    }

    /** Wie viele Zeilen passen in die angegebene Höhe? */
    private fun fittingRows(rows: List<RowDef>, available: Float, scale: Float): Int {
        var used = 0f
        var count = 0
        for (row in rows) {
            val next = used + rowHeightDp(row, scale)
            if (next > available) break
            used = next
            count++
        }
        return count
    }

    /** Eine Zeile des Zeilen-Layouts: bis zu drei Objekte nebeneinander. */
    private fun rowLine(row: RowDef, scale: Float, rowWidth: Float): LayoutElementBuilders.Row {
        val builder = LayoutElementBuilders.Row.Builder()
            .setWidth(DimensionBuilders.expand())

        // Blöcke mit eigener Breite bekommen sie in dp; die übrigen teilen sich den
        // Rest. Hat die Zeile eigene Breiten, werden die übrigen nur so breit wie ihr
        // Inhalt – sonst würde z. B. „188.6 W“ in einer 30-%-Spalte abgeschnitten.
        val items = row.items.take(3)
        val customWidths = items.any { it.width > 0f }
        items.forEach { item ->
            val width = when {
                customWidths && item.width > 0f ->
                    DimensionBuilders.dp((rowWidth * item.width / 100f).coerceAtLeast(24f))

                customWidths -> DimensionBuilders.wrap()

                else -> DimensionBuilders.expand()
            }
            builder.addContent(rowCell(item, scale, width))
        }
        return builder.build()
    }

    /**
     * Ein Objekt in einer Zeile. Die Zelle nimmt den gleichen Anteil der Breite
     * wie die anderen Objekte der Zeile (``expand``), die Ausrichtung aus dem
     * Editor bestimmt die Position des Inhalts darin.
     */
    private fun rowCell(
        item: RowItem,
        scale: Float,
        width: DimensionBuilders.ContainerDimension,
    ): LayoutElementBuilders.Box {
        val align = when (item.align.lowercase()) {
            "left" -> LayoutElementBuilders.HORIZONTAL_ALIGN_START
            "right" -> LayoutElementBuilders.HORIZONTAL_ALIGN_END
            else -> LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
        }
        // Schriftgröße aus dem Editor, angepasst an die Einstellung „Uhr“
        val size = (item.size * scale).coerceIn(8f, 30f)
        val small = (size - 3f).coerceAtLeast(9f)

        val box = LayoutElementBuilders.Box.Builder()
            .setWidth(width)
            .setHorizontalAlignment(align)

        when (item.type) {
            "sensor" -> {
                val label = item.label.orEmpty()
                if (item.labelAbove && label.isNotBlank()) {
                    // Wert unter dem Namen – zwei Zeilen übereinander
                    val sensor = LayoutElementBuilders.Column.Builder()
                        .setHorizontalAlignment(align)
                        .addContent(text(label, LABEL_COLOR, small))
                        .addContent(
                            text(item.text.orEmpty(), parseColor(item.color, Color.WHITE), size)
                        )
                    box.addContent(sensor.build())
                } else {
                    // Wert neben dem Namen (Standard)
                    val sensor = LayoutElementBuilders.Row.Builder()
                        .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                    if (label.isNotBlank()) {
                        sensor.addContent(text(label + " ", LABEL_COLOR, small))
                    }
                    sensor.addContent(
                        text(item.text.orEmpty(), parseColor(item.color, Color.WHITE), size)
                    )
                    box.addContent(sensor.build())
                }
            }

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
                    // Die Reihe füllt die Zelle – nur so hat der dehnbare Titel
                    // Platz und schiebt „An/Aus“ an den rechten Rand.
                    .setWidth(DimensionBuilders.expand())
                    .addContent(
                        LayoutElementBuilders.Image.Builder()
                            .setResourceId(TileIcons.id(item.icon))
                            .setWidth(DimensionBuilders.dp(14f))
                            .setHeight(DimensionBuilders.dp(14f))
                            .build()
                    )
                    // Der Titel dehnt sich aus: Symbol bleibt links, „An/Aus“ rechts,
                    // nur der Titel folgt der eingestellten Ausrichtung.
                    .addContent(
                        LayoutElementBuilders.Box.Builder()
                            .setWidth(DimensionBuilders.expand())
                            .setHorizontalAlignment(align)
                            .addContent(text(" " + item.label.orEmpty(), Color.WHITE, size))
                            .build()
                    )
                val state = item.stateLabel.orEmpty()
                // „An/Aus“ nur zeigen, wenn es im Editor eingeschaltet ist
                if (item.showState && state.isNotBlank()) {
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
        // „An/Aus“ nur zeigen, wenn es im Editor eingeschaltet ist
        if (state.showState) {
            content.addContent(stateText)
        }
        val contentRow = content.build()

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
                    .addContent(contentRow)
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
