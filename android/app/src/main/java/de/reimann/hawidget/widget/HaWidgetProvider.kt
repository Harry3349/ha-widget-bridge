package de.reimann.hawidget.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import de.reimann.hawidget.data.WidgetPrefs

/**
 * AppWidgetProvider des Homescreen-Widgets.
 *
 * Klicks auf Buttons kommen als Broadcast mit [ACTION_PRESS] an; die
 * eigentliche Arbeit (Netzwerk) erledigen WorkManager-Worker.
 */
class HaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Der Launcher ruft das nach Neustart/App-Update auf: hier die
        // periodische Aktualisierung sicherstellen, falls sie verloren ging.
        Widgets.schedulePeriodicRefresh(context)

        // Zuerst sofort eine Ansicht liefern – ohne sie zeigt der Launcher
        // „Widget kann nicht geladen werden“. Danach kommen die echten Werte.
        Widgets.initialView(context, appWidgetIds.toList())
        Widgets.refreshAsync(context, appWidgetIds.toList(), "update")
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )

        when (intent.action) {
            ACTION_PRESS -> {
                val buttonKey = intent.getStringExtra(EXTRA_BUTTON_KEY)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && buttonKey != null) {
                    Widgets.press(context, appWidgetId, buttonKey)
                }
            }

            ACTION_REFRESH -> {
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    Widgets.refreshAsync(context, listOf(appWidgetId), "tap")
                } else {
                    Widgets.refreshAllAsync(context)
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.remove(context, it) }
    }

    companion object {
        const val ACTION_PRESS = "de.reimann.hawidget.action.PRESS"
        const val ACTION_REFRESH = "de.reimann.hawidget.action.REFRESH"
        const val EXTRA_BUTTON_KEY = "button_key"
    }
}
