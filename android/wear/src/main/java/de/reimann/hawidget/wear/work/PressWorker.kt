package de.reimann.hawidget.wear.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.reimann.hawidget.wear.data.HaClient
import de.reimann.hawidget.wear.data.Settings

/** Drückt einen Button in Home Assistant und lädt danach den neuen Zustand. */
class PressWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = Settings(context)
        val buttonKey = inputData.getString(KEY_BUTTON).orEmpty()

        if (!settings.isConfigured || settings.widgetId.isBlank() || buttonKey.isBlank()) {
            return Result.success()
        }

        val client = HaClient(settings.baseUrl, settings.token)
        runCatching { client.press(settings.widgetId, buttonKey) }
            .onSuccess { Log.d(TAG, "Button '$buttonKey' gedrückt") }
            .onFailure { Log.w(TAG, "Button '$buttonKey' fehlgeschlagen: ${it.message}") }

        // Kurz warten, damit Home Assistant den neuen Zustand schon meldet
        Thread.sleep(400)
        runCatching { client.snapshotRaw(settings.widgetId) }
            .onSuccess { settings.snapshotJson = it }

        settings.lastRefresh = System.currentTimeMillis()
        Workers.updateTile(context)
        return Result.success()
    }

    companion object {
        const val KEY_BUTTON = "button"

        private const val TAG = "HAWidgetBridge"
    }
}
