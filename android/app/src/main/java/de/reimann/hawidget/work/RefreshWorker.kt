package de.reimann.hawidget.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.reimann.hawidget.R
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.widget.ScreenState
import de.reimann.hawidget.widget.Widgets

/** Aktualisiert die Inhalte aller (oder ausgewählter) Homescreen-Widgets. */
class RefreshWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = Settings(context)

        // Nur abrufen, wenn das Widget überhaupt sichtbar sein kann: ohne diese
        // Prüfung weckt die App alle X Minuten Funkmodul und Server, obwohl der
        // Bildschirm aus ist und niemand auf den Homescreen schaut.
        if (!ScreenState.isVisible(context)) {
            Log.d(TAG, "Bildschirm aus – Aktualisierung übersprungen")
            return Result.success()
        }

        val ids = inputData.getIntArray(KEY_IDS)
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: Widgets.allIds(context)

        if (ids.isEmpty()) return Result.success()

        if (!settings.isConfigured) {
            ids.forEach { appWidgetId ->
                Widgets.update(
                    context,
                    appWidgetId,
                    null,
                    context.getString(R.string.widget_placeholder),
                )
            }
            return Result.success()
        }

        Widgets.refreshNow(context, settings.client(), ids)
        return Result.success()
    }

    companion object {
        const val KEY_IDS = "ids"

        private const val TAG = "HAWidgetBridge"
    }
}
