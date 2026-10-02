package de.reimann.hawidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import de.reimann.hawidget.R
import de.reimann.hawidget.data.RowDef
import de.reimann.hawidget.data.RowItem
import de.reimann.hawidget.data.ValueState
import de.reimann.hawidget.data.WidgetSnapshot
import de.reimann.hawidget.data.WidgetTheme
import de.reimann.hawidget.icons.MdiIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Baut die RemoteViews eines Widgets.
 *
 * Das Layout enthält fest sechs Button-Zeilen (RemoteViews kann keine Views zur
 * Laufzeit erzeugen); nicht genutzte Zeilen werden ausgeblendet.
 */
object WidgetRenderer {

    private const val MAX_ROWS = 6

    /** Werte-Raster: 6 Zeilen à 3 Felder (12 Werte / 2 Spalten = 6 Zeilen). */
    private const val MAX_VALUE_ROWS = 6
    private const val MAX_VALUE_COLUMNS = 3

    /** Zeilen-Layout: 8 Zeilen à 3 Objekte (Text, Sensor, Button). */
    private const val MAX_ROW_LINES = 8
    private const val MAX_ROW_CELLS = 3

    /** Polsterung des Widgets (siehe `WidgetRoot`) – für die Blockbreiten. */
    private const val ROOT_PADDING_DP = 10f

    /** Fallback, wenn der Launcher keine Größe liefert (Standardgröße 250 dp). */
    private const val DEFAULT_WIDGET_WIDTH_DP = 250f

    private val ROW_IDS = intArrayOf(
        R.id.btn_row_1, R.id.btn_row_2, R.id.btn_row_3,
        R.id.btn_row_4, R.id.btn_row_5, R.id.btn_row_6,
    )

    private val ICON_IDS = intArrayOf(
        R.id.btn_icon_1, R.id.btn_icon_2, R.id.btn_icon_3,
        R.id.btn_icon_4, R.id.btn_icon_5, R.id.btn_icon_6,
    )

    private val LABEL_IDS = intArrayOf(
        R.id.btn_label_1, R.id.btn_label_2, R.id.btn_label_3,
        R.id.btn_label_4, R.id.btn_label_5, R.id.btn_label_6,
    )

    private val STATE_IDS = intArrayOf(
        R.id.btn_state_1, R.id.btn_state_2, R.id.btn_state_3,
        R.id.btn_state_4, R.id.btn_state_5, R.id.btn_state_6,
    )

    /** Reihenfolge: erst alle Felder der Zeile 1, dann Zeile 2 … */
    private val VALUE_ROW_IDS = intArrayOf(
        R.id.value_row_1, R.id.value_row_2, R.id.value_row_3,
        R.id.value_row_4, R.id.value_row_5, R.id.value_row_6,
    )

    private val VALUE_CELL_IDS = intArrayOf(
        R.id.value_cell_1_1, R.id.value_cell_1_2, R.id.value_cell_1_3,
        R.id.value_cell_2_1, R.id.value_cell_2_2, R.id.value_cell_2_3,
        R.id.value_cell_3_1, R.id.value_cell_3_2, R.id.value_cell_3_3,
        R.id.value_cell_4_1, R.id.value_cell_4_2, R.id.value_cell_4_3,
        R.id.value_cell_5_1, R.id.value_cell_5_2, R.id.value_cell_5_3,
        R.id.value_cell_6_1, R.id.value_cell_6_2, R.id.value_cell_6_3,
    )

    private val VALUE_NAME_IDS = intArrayOf(
        R.id.value_name_1_1, R.id.value_name_1_2, R.id.value_name_1_3,
        R.id.value_name_2_1, R.id.value_name_2_2, R.id.value_name_2_3,
        R.id.value_name_3_1, R.id.value_name_3_2, R.id.value_name_3_3,
        R.id.value_name_4_1, R.id.value_name_4_2, R.id.value_name_4_3,
        R.id.value_name_5_1, R.id.value_name_5_2, R.id.value_name_5_3,
        R.id.value_name_6_1, R.id.value_name_6_2, R.id.value_name_6_3,
    )

    private val VALUE_TEXT_IDS = intArrayOf(
        R.id.value_text_1_1, R.id.value_text_1_2, R.id.value_text_1_3,
        R.id.value_text_2_1, R.id.value_text_2_2, R.id.value_text_2_3,
        R.id.value_text_3_1, R.id.value_text_3_2, R.id.value_text_3_3,
        R.id.value_text_4_1, R.id.value_text_4_2, R.id.value_text_4_3,
        R.id.value_text_5_1, R.id.value_text_5_2, R.id.value_text_5_3,
        R.id.value_text_6_1, R.id.value_text_6_2, R.id.value_text_6_3,
    )

    private val ROW_LINE_IDS = intArrayOf(
        R.id.row_1, R.id.row_2, R.id.row_3, R.id.row_4,
        R.id.row_5, R.id.row_6, R.id.row_7, R.id.row_8,
    )

    private val ROW_CELL_IDS = intArrayOf(
        R.id.row_1_cell_1, R.id.row_1_cell_2, R.id.row_1_cell_3,
        R.id.row_2_cell_1, R.id.row_2_cell_2, R.id.row_2_cell_3,
        R.id.row_3_cell_1, R.id.row_3_cell_2, R.id.row_3_cell_3,
        R.id.row_4_cell_1, R.id.row_4_cell_2, R.id.row_4_cell_3,
        R.id.row_5_cell_1, R.id.row_5_cell_2, R.id.row_5_cell_3,
        R.id.row_6_cell_1, R.id.row_6_cell_2, R.id.row_6_cell_3,
        R.id.row_7_cell_1, R.id.row_7_cell_2, R.id.row_7_cell_3,
        R.id.row_8_cell_1, R.id.row_8_cell_2, R.id.row_8_cell_3,
    )

    private val ROW_ICON_IDS = intArrayOf(
        R.id.row_1_icon_1, R.id.row_1_icon_2, R.id.row_1_icon_3,
        R.id.row_2_icon_1, R.id.row_2_icon_2, R.id.row_2_icon_3,
        R.id.row_3_icon_1, R.id.row_3_icon_2, R.id.row_3_icon_3,
        R.id.row_4_icon_1, R.id.row_4_icon_2, R.id.row_4_icon_3,
        R.id.row_5_icon_1, R.id.row_5_icon_2, R.id.row_5_icon_3,
        R.id.row_6_icon_1, R.id.row_6_icon_2, R.id.row_6_icon_3,
        R.id.row_7_icon_1, R.id.row_7_icon_2, R.id.row_7_icon_3,
        R.id.row_8_icon_1, R.id.row_8_icon_2, R.id.row_8_icon_3,
    )

    private val ROW_BEFORE_IDS = intArrayOf(
        R.id.row_1_before_1, R.id.row_1_before_2, R.id.row_1_before_3,
        R.id.row_2_before_1, R.id.row_2_before_2, R.id.row_2_before_3,
        R.id.row_3_before_1, R.id.row_3_before_2, R.id.row_3_before_3,
        R.id.row_4_before_1, R.id.row_4_before_2, R.id.row_4_before_3,
        R.id.row_5_before_1, R.id.row_5_before_2, R.id.row_5_before_3,
        R.id.row_6_before_1, R.id.row_6_before_2, R.id.row_6_before_3,
        R.id.row_7_before_1, R.id.row_7_before_2, R.id.row_7_before_3,
        R.id.row_8_before_1, R.id.row_8_before_2, R.id.row_8_before_3,
    )

    private val ROW_MAIN_IDS = intArrayOf(
        R.id.row_1_main_1, R.id.row_1_main_2, R.id.row_1_main_3,
        R.id.row_2_main_1, R.id.row_2_main_2, R.id.row_2_main_3,
        R.id.row_3_main_1, R.id.row_3_main_2, R.id.row_3_main_3,
        R.id.row_4_main_1, R.id.row_4_main_2, R.id.row_4_main_3,
        R.id.row_5_main_1, R.id.row_5_main_2, R.id.row_5_main_3,
        R.id.row_6_main_1, R.id.row_6_main_2, R.id.row_6_main_3,
        R.id.row_7_main_1, R.id.row_7_main_2, R.id.row_7_main_3,
        R.id.row_8_main_1, R.id.row_8_main_2, R.id.row_8_main_3,
    )

    /**
     * Nur für Buttons: der Titel füllt den Platz zwischen Symbol (links) und
     * „An/Aus“ (rechts) – nur seine Ausrichtung ist einstellbar.
     */
    private val ROW_TITLE_IDS = intArrayOf(
        R.id.row_1_title_1, R.id.row_1_title_2, R.id.row_1_title_3,
        R.id.row_2_title_1, R.id.row_2_title_2, R.id.row_2_title_3,
        R.id.row_3_title_1, R.id.row_3_title_2, R.id.row_3_title_3,
        R.id.row_4_title_1, R.id.row_4_title_2, R.id.row_4_title_3,
        R.id.row_5_title_1, R.id.row_5_title_2, R.id.row_5_title_3,
        R.id.row_6_title_1, R.id.row_6_title_2, R.id.row_6_title_3,
        R.id.row_7_title_1, R.id.row_7_title_2, R.id.row_7_title_3,
        R.id.row_8_title_1, R.id.row_8_title_2, R.id.row_8_title_3,
    )

    private val ROW_AFTER_IDS = intArrayOf(
        R.id.row_1_after_1, R.id.row_1_after_2, R.id.row_1_after_3,
        R.id.row_2_after_1, R.id.row_2_after_2, R.id.row_2_after_3,
        R.id.row_3_after_1, R.id.row_3_after_2, R.id.row_3_after_3,
        R.id.row_4_after_1, R.id.row_4_after_2, R.id.row_4_after_3,
        R.id.row_5_after_1, R.id.row_5_after_2, R.id.row_5_after_3,
        R.id.row_6_after_1, R.id.row_6_after_2, R.id.row_6_after_3,
        R.id.row_7_after_1, R.id.row_7_after_2, R.id.row_7_after_3,
        R.id.row_8_after_1, R.id.row_8_after_2, R.id.row_8_after_3,
    )

    fun render(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshot?,
        status: String?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ha)
        val theme = snapshot?.theme ?: WidgetTheme()

        // Grundflächen und Texte
        views.setInt(R.id.widget_root, "setBackgroundColor", color(theme.background, Color.TRANSPARENT))
        views.setTextColor(R.id.widget_title, color(theme.accent, Color.parseColor("#FF00E676")))
        views.setTextColor(R.id.widget_content, color(theme.textColor, Color.WHITE))
        views.setTextViewText(
            R.id.widget_title,
            snapshot?.name ?: context.getString(R.string.widget_default_title),
        )
        views.setTextViewTextSize(
            R.id.widget_content,
            TypedValue.COMPLEX_UNIT_SP,
            snapshot?.textSize ?: 14f,
        )

        // Inhalt (Template, Zeilen-Layout oder – bei einer Spalte – Werteliste)
        val html = snapshot?.html.orEmpty()
        val values = snapshot?.values.orEmpty()
        val columns = (snapshot?.valueColumns ?: 1).coerceIn(1, MAX_VALUE_COLUMNS)
        val rows = snapshot?.rows.orEmpty()
        val hasRows = rows.any { it.items.isNotEmpty() }

        // Werte nur dann als Raster anordnen, wenn sie nicht aus einem
        // Jinja-Template stammen und tatsächlich nebeneinander sollen.
        val grid = !hasRows && values.isNotEmpty() &&
            snapshot?.templateUsed != true && columns > 1

        // Zeilen-Layout: nur ein eigenes Jinja-Template darf zusätzlich stehen
        val showContent = !grid && html.isNotBlank() &&
            (!hasRows || snapshot?.templateUsed == true)
        views.setViewVisibility(R.id.widget_content, if (showContent) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.value_area, if (grid) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.row_area, if (hasRows) View.VISIBLE else View.GONE)

        if (grid) {
            renderValueGrid(
                views = views,
                values = values,
                columns = columns,
                labelAbove = snapshot?.valueLabelAbove == true,
                textSize = snapshot?.textSize ?: 14f,
                textColor = color(theme.textColor, Color.WHITE),
            )
        }

        when {
            html.isNotBlank() -> views.setTextViewText(
                R.id.widget_content,
                Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY),
            )

            !status.isNullOrBlank() -> views.setTextViewText(R.id.widget_content, status)
            else -> views.setTextViewText(
                R.id.widget_content,
                context.getString(R.string.widget_placeholder),
            )
        }

        views.setTextViewText(R.id.widget_updated, status ?: timeStamp())

        // Buttons: entweder klassische Button-Zeilen oder das Zeilen-Layout
        val buttons = if (hasRows) emptyList() else snapshot?.buttons.orEmpty()
        for (index in 0 until MAX_ROWS) {
            val rowId = ROW_IDS[index]
            val button = buttons.getOrNull(index)

            if (button == null) {
                views.setViewVisibility(rowId, View.GONE)
                views.setOnClickPendingIntent(rowId, null)
                continue
            }

            views.setViewVisibility(rowId, View.VISIBLE)
            views.setImageViewResource(ICON_IDS[index], MdiIcons.drawable(button.icon))
            views.setTextViewText(LABEL_IDS[index], button.label)
            // „An/Aus“ nur zeigen, wenn es im Editor eingeschaltet ist
            if (button.showState) {
                views.setTextViewText(STATE_IDS[index], button.stateLabel)
                views.setViewVisibility(STATE_IDS[index], View.VISIBLE)
            } else {
                views.setViewVisibility(STATE_IDS[index], View.GONE)
            }
            views.setInt(
                rowId,
                "setBackgroundResource",
                when {
                    !button.available -> R.drawable.widget_button_error
                    button.active -> R.drawable.widget_button_active
                    else -> R.drawable.widget_button
                },
            )
            views.setOnClickPendingIntent(
                rowId,
                pressIntent(context, appWidgetId, button.key),
            )
        }

        if (hasRows) {
            renderRows(
                views = views,
                rows = rows,
                textColor = color(theme.textColor, Color.WHITE),
                rowWidthDp = rowWidthDp(context, appWidgetId),
                pressFor = { key -> pressIntent(context, appWidgetId, key) },
            )
        }

        // Ganze Fläche antippen = neu laden
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context, appWidgetId))

        return views
    }

    // -------------------------------------------------------------- intern

    /**
     * Zeilen-Layout zeichnen: Zeile für Zeile, Objekt für Objekt.
     *
     * Leere Zellen werden ausgeblendet – die übrigen teilen sich die
     * Zeilenbreite (layout_weight). Die Ausrichtung jedes Objekts setzt
     * ``setHorizontalGravity`` auf der Zelle (LinearLayout ist remotable).
     */
    private fun renderRows(
        views: RemoteViews,
        rows: List<RowDef>,
        textColor: Int,
        rowWidthDp: Float,
        pressFor: (String) -> PendingIntent,
    ) {
        for (line in 0 until MAX_ROW_LINES) {
            val row = rows.getOrNull(line)
            val lineId = ROW_LINE_IDS[line]

            if (row == null || row.items.isEmpty()) {
                views.setViewVisibility(lineId, View.GONE)
                for (cell in 0 until MAX_ROW_CELLS) {
                    val cellId = ROW_CELL_IDS[line * MAX_ROW_CELLS + cell]
                    views.setViewVisibility(cellId, View.GONE)
                    views.setOnClickPendingIntent(cellId, null)
                }
                continue
            }

            views.setViewVisibility(lineId, View.VISIBLE)

            // Blockbreiten: nur wenn der Nutzer Breiten eingestellt hat, bekommen
            // die Zellen eine eigene Breite (sonst teilen sie sich gleichmäßig).
            val customWidths = row.items.any { it.width > 0f }
            val flexible = row.items.count { it.width <= 0f }
            val fixedDp = row.items.filter { it.width > 0f }
                .sumOf { (it.width / 100f * rowWidthDp).toDouble() }.toFloat()
            val flexibleDp = if (flexible > 0) {
                ((rowWidthDp - fixedDp) / flexible).coerceAtLeast(24f)
            } else {
                0f
            }

            for (cell in 0 until MAX_ROW_CELLS) {
                val slot = line * MAX_ROW_CELLS + cell
                val cellId = ROW_CELL_IDS[slot]
                val item = row.items.getOrNull(cell)

                if (item == null) {
                    views.setViewVisibility(cellId, View.GONE)
                    views.setOnClickPendingIntent(cellId, null)
                    continue
                }

                val widthDp = if (!customWidths) {
                    null
                } else if (item.width > 0f) {
                    (item.width / 100f * rowWidthDp).coerceAtLeast(16f)
                } else {
                    flexibleDp
                }

                renderRowItem(views, slot, item, textColor, widthDp, pressFor)
            }
        }
    }

    private fun renderRowItem(
        views: RemoteViews,
        slot: Int,
        item: RowItem,
        textColor: Int,
        widthDp: Float?,
        pressFor: (String) -> PendingIntent,
    ) {
        val cellId = ROW_CELL_IDS[slot]
        val iconId = ROW_ICON_IDS[slot]
        val beforeId = ROW_BEFORE_IDS[slot]
        val mainId = ROW_MAIN_IDS[slot]
        val titleId = ROW_TITLE_IDS[slot]
        val afterId = ROW_AFTER_IDS[slot]

        val size = item.size.coerceIn(8f, 30f)
        val smallSize = (size - 3f).coerceAtLeast(9f)

        views.setViewVisibility(cellId, View.VISIBLE)
        // Bei Buttons bestimmt nicht die Zelle die Lage, sondern das dehnbare
        // Titelfeld (Symbol links, An/Aus rechts).
        views.setInt(
            cellId,
            "setHorizontalGravity",
            if (item.type == "button") Gravity.START else alignGravity(item.align),
        )
        // 0 = kein Hintergrund (nur Buttons bekommen gleich eine Fläche)
        views.setInt(cellId, "setBackgroundResource", 0)
        views.setOnClickPendingIntent(cellId, null)

        // Eigene Blockbreite (ab Android 12; sonst teilen sich die Blöcke die Zeile)
        if (widthDp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setViewLayoutWidth(cellId, widthDp, TypedValue.COMPLEX_UNIT_DIP)
        }

        // Alle Slots erst einmal leeren
        views.setViewVisibility(iconId, View.GONE)
        views.setViewVisibility(beforeId, View.GONE)
        views.setViewVisibility(mainId, View.GONE)
        views.setViewVisibility(titleId, View.GONE)
        views.setViewVisibility(afterId, View.GONE)

        when (item.type) {
            "sensor" -> {
                views.setTextViewTextSize(mainId, TypedValue.COMPLEX_UNIT_SP, size)
                views.setTextColor(mainId, color(item.color, textColor))
                views.setTextViewText(mainId, item.text.orEmpty())
                views.setViewVisibility(mainId, View.VISIBLE)

                val label = item.label.orEmpty()
                if (label.isNotBlank()) {
                    views.setTextViewTextSize(beforeId, TypedValue.COMPLEX_UNIT_SP, smallSize)
                    views.setTextViewText(beforeId, label)
                    views.setViewVisibility(beforeId, View.VISIBLE)
                }
            }

            "button" -> {
                views.setImageViewResource(iconId, MdiIcons.drawable(item.icon))
                views.setViewVisibility(iconId, View.VISIBLE)

                // Symbol bleibt links, „An/Aus“ rechts – nur der Titel wandert
                views.setTextViewTextSize(titleId, TypedValue.COMPLEX_UNIT_SP, size)
                views.setTextColor(titleId, textColor)
                views.setTextViewText(titleId, item.label.orEmpty())
                views.setInt(titleId, "setGravity", alignGravity(item.align))
                views.setViewVisibility(titleId, View.VISIBLE)

                val stateLabel = item.stateLabel.orEmpty()
                // „An/Aus“ nur zeigen, wenn es im Editor eingeschaltet ist
                if (item.showState && stateLabel.isNotBlank()) {
                    views.setTextViewTextSize(afterId, TypedValue.COMPLEX_UNIT_SP, smallSize)
                    views.setTextViewText(afterId, stateLabel)
                    views.setViewVisibility(afterId, View.VISIBLE)
                }

                views.setInt(
                    cellId,
                    "setBackgroundResource",
                    when {
                        !item.available -> R.drawable.widget_button_error
                        item.active -> R.drawable.widget_button_active
                        else -> R.drawable.widget_button
                    },
                )
                item.key?.let { key -> views.setOnClickPendingIntent(cellId, pressFor(key)) }
            }

            else -> {
                views.setTextViewTextSize(mainId, TypedValue.COMPLEX_UNIT_SP, size)
                views.setTextColor(mainId, color(item.color, textColor))
                views.setTextViewText(mainId, item.text.orEmpty())
                views.setViewVisibility(mainId, View.VISIBLE)
            }
        }
    }

    private fun alignGravity(align: String): Int = when (align.lowercase()) {
        "left" -> Gravity.START
        "right" -> Gravity.END
        else -> Gravity.CENTER_HORIZONTAL
    }

    /**
     * Breite, die den Zeilen im Widget zur Verfügung steht (dp).
     *
     * Der Launcher legt die Widget-Größe fest; daraus ergeben sich die
     * Prozentangaben für die Blöcke.
     */
    private fun rowWidthDp(context: Context, appWidgetId: Int): Float {
        val options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)
        val width = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val widgetWidth = if (width > 0) width.toFloat() else DEFAULT_WIDGET_WIDTH_DP
        return (widgetWidth - ROOT_PADDING_DP * 2f).coerceAtLeast(80f)
    }

    /**
     * Werte in die vorab deklarierten Felder schreiben (zeilenweise gefüllt).
     * Nicht benötigte Zeilen und Felder werden ausgeblendet; dadurch nutzen die
     * sichtbaren Felder die volle Breite und stehen nebeneinander.
     */
    private fun renderValueGrid(
        views: RemoteViews,
        values: List<ValueState>,
        columns: Int,
        labelAbove: Boolean,
        textSize: Float,
        textColor: Int,
    ) {
        val nameSize = (textSize - 3f).coerceAtLeast(9f)

        for (row in 0 until MAX_VALUE_ROWS) {
            var rowHasValue = false

            for (col in 0 until MAX_VALUE_COLUMNS) {
                val slot = row * MAX_VALUE_COLUMNS + col
                val value = if (col < columns) values.getOrNull(row * columns + col) else null

                if (value == null) {
                    views.setViewVisibility(VALUE_CELL_IDS[slot], View.GONE)
                    continue
                }

                rowHasValue = true
                views.setViewVisibility(VALUE_CELL_IDS[slot], View.VISIBLE)

                val nameId = VALUE_NAME_IDS[slot]
                val textId = VALUE_TEXT_IDS[slot]
                val valueColor = color(value.color, textColor)

                views.setTextViewTextSize(textId, TypedValue.COMPLEX_UNIT_SP, textSize)
                views.setTextViewTextSize(nameId, TypedValue.COMPLEX_UNIT_SP, nameSize)

                if (labelAbove) {
                    views.setViewVisibility(nameId, View.VISIBLE)
                    views.setTextViewText(nameId, value.label)
                    views.setTextViewText(textId, value.text)
                    views.setTextColor(textId, valueColor)
                } else {
                    views.setViewVisibility(nameId, View.GONE)
                    views.setTextViewText(textId, Html.fromHtml(inlineHtml(value), Html.FROM_HTML_MODE_LEGACY))
                    views.setTextColor(textId, textColor)
                }
            }

            views.setViewVisibility(VALUE_ROW_IDS[row], if (rowHasValue) View.VISIBLE else View.GONE)
        }
    }

    /** ``Name · Wert`` als HTML, damit Name fett und Wert farbig ist. */
    private fun inlineHtml(value: ValueState): String =
        "<b>${escape(value.label)}</b> · " +
            "<font color='${escape(value.color)}'>${escape(value.text)}</font>"

    /** Texte aus Home Assistant vor der HTML-Ausgabe entschärfen. */
    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun pressIntent(context: Context, appWidgetId: Int, buttonKey: String): PendingIntent {
        val intent = Intent(context, HaWidgetProvider::class.java).apply {
            action = HaWidgetProvider.ACTION_PRESS
            // Eindeutige data-URI, damit sich die PendingIntents unterscheiden
            data = Uri.parse("hawidget://press/$appWidgetId/$buttonKey")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(HaWidgetProvider.EXTRA_BUTTON_KEY, buttonKey)
        }
        val requestCode = appWidgetId * 1000 + (buttonKey.hashCode() and 0xFF)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun refreshIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, HaWidgetProvider::class.java).apply {
            action = HaWidgetProvider.ACTION_REFRESH
            data = Uri.parse("hawidget://refresh/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            appWidgetId * 1000 + 999,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun color(hex: String?, fallback: Int): Int {
        if (hex.isNullOrBlank()) return fallback
        return runCatching { Color.parseColor(hex) }.getOrDefault(fallback)
    }

    private fun timeStamp(): String =
        SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date())
}
