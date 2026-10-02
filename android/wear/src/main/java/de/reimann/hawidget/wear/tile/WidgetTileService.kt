package de.reimann.hawidget.wear.tile

import android.util.Log
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.ResourceBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.TimelineBuilders
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson
import de.reimann.hawidget.wear.work.Workers

/**
 * Die Tile auf der Uhr.
 *
 * Sie zeichnet den zuletzt gespeicherten Snapshot (schnell, ohne Netz im
 * Dienst) und stößt im Hintergrund eine Aktualisierung an. Ein Button-Klick
 * kommt über ``currentState.lastClickableId`` herein und wird an den
 * [de.reimann.hawidget.wear.work.PressWorker] übergeben.
 */
class WidgetTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val clickId = requestParams.currentState.lastClickableId
        val pressed = !clickId.isNullOrBlank() && clickId.startsWith(TileRenderer.PRESS_PREFIX)
        if (pressed) {
            val key = clickId.removePrefix(TileRenderer.PRESS_PREFIX)
            Log.d(TAG, "Button geklickt: $key")
            Workers.press(this, key)
        }

        val layout = TileRenderer.render(this, loadSnapshot(), pressed)
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()

        // Im Hintergrund nachladen (gedrosselt) und danach die Tile neu zeichnen
        Workers.refresh(this)
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    private fun loadSnapshot(): de.reimann.hawidget.wear.data.WidgetSnapshot? {
        val raw = Settings(this).snapshotJson ?: return null
        return runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MILLIS = 15 * 60 * 1000L
    }
}
