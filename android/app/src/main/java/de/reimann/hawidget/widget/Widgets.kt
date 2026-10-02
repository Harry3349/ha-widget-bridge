package de.reimann.hawidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.reimann.hawidget.R
import de.reimann.hawidget.data.HaClient
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetJson
import de.reimann.hawidget.data.WidgetPrefs
import de.reimann.hawidget.data.WidgetSnapshot
import de.reimann.hawidget.wear.Watches
import de.reimann.hawidget.wear.WearSync
import de.reimann.hawidget.work.PressWorker
import de.reimann.hawidget.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Zentrale Helfer rund um die Homescreen-Widgets. */
object Widgets {

    // ----------------------------------------------------------- Instanzen

    fun allIds(context: Context): List<Int> =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HaWidgetProvider::class.java))
            .toList()

    fun update(context: Context, appWidgetId: Int, snapshot: WidgetSnapshot?, status: String?) {
        val views = WidgetRenderer.render(context, appWidgetId, snapshot, status)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    fun cachedSnapshot(context: Context, widgetId: String): WidgetSnapshot? =
        WidgetPrefs.loadSnapshot(context, widgetId)?.let { raw ->
            runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
        }

    /**
     * Sofort eine Ansicht liefern, damit der Launcher nicht "Widget kann nicht
     * geladen werden" anzeigt. Danach folgt die Aktualisierung im Hintergrund.
     */
    fun initialView(context: Context, ids: List<Int>) {
        for (appWidgetId in ids) {
            val widgetId = WidgetPrefs.widgetId(context, appWidgetId)
            val snapshot = widgetId?.let { cachedSnapshot(context, it) }
            val status = when {
                widgetId == null -> context.getString(R.string.widget_placeholder)
                snapshot == null -> context.getString(R.string.widget_loading)
                else -> null
            }
            update(context, appWidgetId, snapshot, status)
        }
    }

    // ------------------------------------------------------ Aktualisieren

    fun refreshAsync(context: Context, ids: List<Int>, reason: String = "manual") {
        val targets = ids.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        if (targets.isEmpty()) return

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(workDataOf(RefreshWorker.KEY_IDS to targets.toIntArray()))
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("hawidget-refresh-$reason", ExistingWorkPolicy.REPLACE, request)
    }

    fun refreshAllAsync(context: Context) = refreshAsync(context, allIds(context), "all")

    /** Alle übergebenen Widgets sofort aktualisieren (im Hintergrund-Thread aufrufen). */
    suspend fun refreshNow(context: Context, client: HaClient, ids: List<Int>) {
        assignMissingWidgets(context, client, ids)

        val cache = HashMap<String, WidgetSnapshot?>()
        val failed = HashSet<String>()

        for (appWidgetId in ids) {
            val widgetId = WidgetPrefs.widgetId(context, appWidgetId)
            if (widgetId == null) {
                // In Home Assistant ist noch gar kein Widget angelegt
                update(context, appWidgetId, null, context.getString(R.string.widget_no_widget))
                continue
            }

            if (!cache.containsKey(widgetId)) {
                val raw = runCatching { client.snapshotRaw(widgetId) }.getOrNull()
                if (raw != null) {
                    WidgetPrefs.saveSnapshot(context, widgetId, raw)
                    cache[widgetId] = runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
                } else {
                    failed.add(widgetId)
                    cache[widgetId] = cachedSnapshot(context, widgetId)
                }
            }

            val snapshot = cache[widgetId]
            val status = when {
                widgetId in failed -> context.getString(R.string.widget_error)
                snapshot == null -> context.getString(R.string.widget_placeholder)
                else -> null
            }
            update(context, appWidgetId, snapshot, status)
        }
    }

    /**
     * Homescreen-Widgets ohne Zuordnung übernehmen das erste in Home Assistant
     * angelegte Widget. Nötig, weil beim Anheften (``requestPinAppWidget``) die
     * Konfigurations-Activity nicht immer läuft – ohne Zuordnung würde das Widget
     * leer bleiben („Widget kann nicht geladen werden“).
     */
    private suspend fun assignMissingWidgets(
        context: Context,
        client: HaClient,
        ids: List<Int>,
    ) {
        val missing = ids.filter { WidgetPrefs.widgetId(context, it) == null }
        if (missing.isEmpty()) return

        // Nur Widgets, die auch fürs Handy gedacht sind (keine reinen Uhr-Fassungen)
        val first = runCatching {
            client.listWidgets().firstOrNull { !it.isWatchOnly }?.id
        }.getOrNull() ?: return
        missing.forEach { WidgetPrefs.setWidgetId(context, it, first) }
    }

    fun schedulePeriodicRefresh(context: Context) {
        val minutes = Settings(context).refreshMinutes.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "hawidget-periodic",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    // ---------------------------------------------------------- Buttons

    fun press(context: Context, appWidgetId: Int, buttonKey: String) {
        val request = OneTimeWorkRequestBuilder<PressWorker>()
            .setInputData(
                workDataOf(
                    PressWorker.KEY_APPWIDGET_ID to appWidgetId,
                    PressWorker.KEY_BUTTON_KEY to buttonKey,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
