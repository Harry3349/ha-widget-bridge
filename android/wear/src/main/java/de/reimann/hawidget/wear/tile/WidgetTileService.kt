package de.reimann.hawidget.wear.tile

import android.util.Log
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import de.reimann.hawidget.wear.data.HaClient
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson
import de.reimann.hawidget.wear.data.WidgetSnapshot
import de.reimann.hawidget.wear.work.Workers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Die Tile auf der Uhr.
 *
 * Ein Button-Klick kommt über ``currentState.lastClickableId`` herein. Der Druck
 * wird **direkt hier** ausgeführt und der Snapshot sofort neu geholt – die Tile,
 * die der Dienst zurückgibt, zeigt damit unmittelbar den neuen Zustand
 * (über WorkManager dauerte die Rückmeldung spürbar lange). Außerdem lädt der
 * Dienst den Snapshot beim Anzeigen selbst nach, wenn er älter als eine Minute
 * ist, damit die Zeitzeile „Stand …“ stimmt.
 */
class WidgetTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val clickId = requestParams.currentState.lastClickableId
        val pressedKey = clickId
            ?.takeIf { it.startsWith(TileRenderer.PRESS_PREFIX) }
            ?.removePrefix(TileRenderer.PRESS_PREFIX)

        val settings = Settings(this)
        var snapshot = loadSnapshot()
        var note: String? = null

        if (!pressedKey.isNullOrBlank()) {
            Log.d(TAG, "Button geklickt: $pressedKey")
            // Direkt schalten (kurze Zeitlimits): nur so zeigt die Tile, die wir
            // jetzt zurückgeben, schon den neuen Zustand. Kein automatischer
            // zweiter Versuch – der könnte das Gerät wieder zurückschalten.
            val fresh = runBlocking { pressAndRefresh(settings, pressedKey) }
            if (fresh != null) {
                snapshot = fresh
            } else {
                note = getString(R.string.tile_press_failed)
            }
        } else if (settings.isConfigured && isStale(settings)) {
            // Beim Anzeigen selbst nachladen, damit „Stand“ wirklich aktuell ist
            runBlocking { fetchSnapshot(settings) }?.let { snapshot = it }
        }

        val layout = TileRenderer.render(this, snapshot, settings.lastRefresh, note)
        val timeline = TimelineBuilders.Timeline.Builder()
            .addTimelineEntry(
                TimelineBuilders.TimelineEntry.Builder()
                    .setLayout(LayoutElementBuilders.Layout.Builder().setRoot(layout).build())
                    .build()
            )
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(timeline)
            .build()

        // Nach einem Fehlversuch im Hintergrund nachfassen (nur lesen, nicht schalten)
        if (note != null) Workers.refresh(this, force = true)
        return Futures.immediateFuture(tile)
    }

    /** Snapshot ist älter als beim Anzeigen akzeptabel. */
    private fun isStale(settings: Settings): Boolean =
        System.currentTimeMillis() - settings.lastRefresh > DISPLAY_REFRESH_MS

    /**
     * Button drücken und den neuen Zustand holen.
     *
     * Blockiert bewusst kurz (kurze Zeitlimits im [HaClient]): der Systemaufruf
     * für die Tile wartet darauf, und nur so erscheint die Rückmeldung sofort.
     * Rückgabe ``null`` bedeutet: hat nicht geklappt (dann wird **nicht** erneut
     * gedrückt, sonst schaltet das Gerät wieder zurück).
     */
    private suspend fun pressAndRefresh(settings: Settings, buttonKey: String): WidgetSnapshot? {
        val client = client(settings) ?: return null
        val widgetId = resolveWidgetId(settings, client) ?: return null

        val pressed = runCatching { client.press(widgetId, buttonKey) }
        if (pressed.isFailure) {
            Log.w(TAG, "Druck fehlgeschlagen: ${pressed.exceptionOrNull()?.message}")
            return null
        }

        // Home Assistant meldet den neuen Zustand nicht immer sofort
        delay(SETTLE_MS)
        return fetchSnapshot(settings, client, widgetId)
    }

    private suspend fun fetchSnapshot(settings: Settings): WidgetSnapshot? {
        val client = client(settings) ?: return null
        val widgetId = resolveWidgetId(settings, client) ?: return null
        return fetchSnapshot(settings, client, widgetId)
    }

    private fun client(settings: Settings): HaClient? {
        if (!settings.isConfigured) return null
        return HaClient(settings.baseUrl, settings.token, timeoutSeconds = TILE_TIMEOUT_SECONDS)
    }

    private suspend fun resolveWidgetId(settings: Settings, client: HaClient): String? {
        var widgetId = settings.widgetId
        if (widgetId.isBlank()) {
            widgetId = runCatching { WidgetJson.firstWidgetId(client.listWidgets()) }
                .getOrNull()
                .orEmpty()
            if (widgetId.isBlank()) return null
            settings.widgetId = widgetId
        }
        return widgetId
    }

    private suspend fun fetchSnapshot(
        settings: Settings,
        client: HaClient,
        widgetId: String,
    ): WidgetSnapshot? {
        val raw = runCatching { client.snapshotRaw(widgetId) }.getOrNull() ?: return null
        settings.snapshotJson = raw
        settings.lastRefresh = System.currentTimeMillis()
        return runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    private fun loadSnapshot(): WidgetSnapshot? {
        val raw = Settings(this).snapshotJson ?: return null
        return runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
        /** Kurze Zeitlimits: der Klick soll schnell beantwortet sein. */
        private const val TILE_TIMEOUT_SECONDS = 3L
        /** Kurz warten, bis Home Assistant den neuen Zustand meldet. */
        private const val SETTLE_MS = 350L
        /** Beim Anzeigen nachladen, wenn der Snapshot älter ist als das hier. */
        private const val DISPLAY_REFRESH_MS = 60_000L
        private const val RESOURCES_VERSION = "1"
        private const val FRESHNESS_MILLIS = 15 * 60 * 1000L
    }
}
