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

        // Nach einer Änderung in der App (Speichern, Zuordnen …) wird immer
        // gearbeitet – auch wenn der Bildschirm schon aus ist. Sonst behält die
        // Uhr den alten Stand, bis der Nutzer das nächste Mal hinschaut.
        val force = inputData.getBoolean(KEY_FORCE, false)

        // Sonst nur abrufen, wenn das Widget überhaupt sichtbar sein kann: ohne
        // diese Prüfung weckt die App alle X Minuten Funkmodul und Server, obwohl
        // der Bildschirm aus ist und niemand auf den Homescreen schaut.
        if (!force && !ScreenState.isVisible(context)) {
            Log.d(TAG, "Bildschirm aus – Aktualisierung übersprungen")
            return Result.success()
        }

        val ids = inputData.getIntArray(KEY_IDS)
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: Widgets.allIds(context)

        // Ohne Homescreen-Widget gibt es trotzdem etwas zu tun: die Uhren
        // brauchen ihren Stand (dann läuft die Schleife leer, der Push nicht).
        if (ids.isEmpty() && !force) return Result.success()

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

        Widgets.refreshNow(context, settings.client(), ids, forceWatch = force)

        // Selbstheilung: geht die periodische Planung verloren (kommt nach
        // App-Updates vor), setzt jeder Lauf sie wieder.
        Widgets.schedulePeriodicRefresh(context)
        return Result.success()
    }

    companion object {
        const val KEY_IDS = "ids"

        /** true = auch bei ausgeschaltetem Bildschirm abrufen und übertragen. */
        const val KEY_FORCE = "force"

        private const val TAG = "HAWidgetBridge"
    }
}
