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
import de.reimann.hawidget.work.PressWorker
import de.reimann.hawidget.work.RefreshWorker
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
        val cache = HashMap<String, WidgetSnapshot?>()
        val failed = HashSet<String>()

        for (appWidgetId in ids) {
            val widgetId = WidgetPrefs.widgetId(context, appWidgetId) ?: continue

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
