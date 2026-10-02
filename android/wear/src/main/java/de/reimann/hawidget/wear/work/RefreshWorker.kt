package de.reimann.hawidget.wear.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.reimann.hawidget.wear.data.HaClient
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson

/** Holt den Snapshot aus Home Assistant und legt ihn für die Tile ab. */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = Settings(context)
        if (!settings.isConfigured) {
            Workers.updateTile(context)
            return Result.success()
        }

        val client = HaClient(settings.baseUrl, settings.token)

        var widgetId = settings.widgetId
        if (widgetId.isBlank()) {
            widgetId = runCatching { WidgetJson.firstWidgetId(client.listWidgets()) }
                .getOrNull()
                .orEmpty()
            if (widgetId.isNotBlank()) settings.widgetId = widgetId
        }

        if (widgetId.isNotBlank()) {
            runCatching { client.snapshotRaw(widgetId) }
                .onSuccess { settings.snapshotJson = it }
                .onFailure { Log.w(TAG, "Snapshot fehlgeschlagen: ${it.message}") }
        }

        settings.lastRefresh = System.currentTimeMillis()
        Workers.schedulePeriodic(context)
        Workers.updateTile(context)
        return Result.success()
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
    }
}
