package de.reimann.hawidget.wear.work

import android.content.Context
import androidx.wear.tiles.TileService
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.tile.WidgetTileService
import java.util.concurrent.TimeUnit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf

/** Zentrale Helfer rund um die Tile. */
object Workers {

    /** Nicht bei jedem Zeichnen der Tile neu abrufen. */
    private const val REFRESH_THROTTLE_MS = 60_000L
    private const val UNIQUE_REFRESH = "hawidget-refresh"
    private const val UNIQUE_PRESS = "hawidget-press"
    private const val UNIQUE_PERIODIC = "hawidget-periodic"

    fun refresh(context: Context, force: Boolean = false) {
        val settings = Settings(context)
        if (!settings.isConfigured) return
        if (!force && System.currentTimeMillis() - settings.lastRefresh < REFRESH_THROTTLE_MS) return

        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_REFRESH, ExistingWorkPolicy.KEEP, request)
    }

    fun press(context: Context, buttonKey: String) {
        val request = OneTimeWorkRequestBuilder<PressWorker>()
            .setInputData(workDataOf(PressWorker.KEY_BUTTON to buttonKey))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(UNIQUE_PRESS, ExistingWorkPolicy.REPLACE, request)
    }

    /** Selbstheilende Planung: mindestens alle 15 Minuten aktualisieren. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(UNIQUE_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun updateTile(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(WidgetTileService::class.java) }
    }
}
