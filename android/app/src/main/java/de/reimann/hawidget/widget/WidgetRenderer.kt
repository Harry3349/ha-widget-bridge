package de.reimann.hawidget.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.text.Html
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import de.reimann.hawidget.R
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

    fun render(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetSnapshot?,
        status: String?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_ha)
        val theme = snapshot?.theme ?: WidgetTheme()

        // Grundflächen und Texte
        views.setInt(R.id.widget_root, "setBackgroundColor", color(theme.background, Color.parseColor("#E6101018")))
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

        // Inhalt
        val html = snapshot?.html.orEmpty()
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

        // Buttons
        val buttons = snapshot?.buttons.orEmpty()
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
            views.setTextViewText(STATE_IDS[index], button.stateLabel)
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

        // Ganze Fläche antippen = neu laden
        views.setOnClickPendingIntent(R.id.widget_root, refreshIntent(context, appWidgetId))

        return views
    }

    // -------------------------------------------------------------- intern

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
