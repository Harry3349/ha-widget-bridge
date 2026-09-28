package de.reimann.hawidget.work

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.reimann.hawidget.R
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetPrefs
import de.reimann.hawidget.widget.Widgets
import kotlinx.coroutines.delay

/** Drückt einen Widget-Button in Home Assistant und aktualisiert danach die Anzeige. */
class PressWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext

        val appWidgetId = inputData.getInt(
            KEY_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        val buttonKey = inputData.getString(KEY_BUTTON_KEY) ?: return Result.failure()
        val widgetId = WidgetPrefs.widgetId(context, appWidgetId) ?: return Result.failure()

        val settings = Settings(context)
        if (!settings.isConfigured) return Result.failure()

        val client = settings.client()

        return try {
            client.press(widgetId, buttonKey)
            // Home Assistant kurz Zeit geben, den neuen Zustand zu schreiben
            delay(STATE_DELAY_MS)
            Widgets.refreshNow(context, client, listOf(appWidgetId))
            Result.success()
        } catch (error: Exception) {
            Widgets.update(
                context,
                appWidgetId,
                Widgets.cachedSnapshot(context, widgetId),
                context.getString(R.string.action_press_failed),
            )
            Result.failure()
        }
    }

    companion object {
        const val KEY_APPWIDGET_ID = "app_widget_id"
        const val KEY_BUTTON_KEY = "button_key"
        private const val STATE_DELAY_MS = 600L
    }
}
