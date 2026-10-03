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
import de.reimann.hawidget.wear.R
import de.reimann.hawidget.wear.data.Bridge
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson
import de.reimann.hawidget.wear.data.WidgetSnapshot
import de.reimann.hawidget.wear.icons.TileIcons

/**
 * Die Tile auf der Uhr.
 *
 * Die Uhr hat meist **kein WLAN**: Sie zeichnet den Snapshot, den die Handy-App
 * über den Wearable Data Layer ablegt. Ein Button-Klick wird als kurze Nachricht
 * an das Handy geschickt; die Handy-App ruft den Dienst in Home Assistant auf und
 * überträgt den neuen Stand zurück, woraufhin die Tile neu gezeichnet wird.
 *
 * Beim Anzeigen bittet die Tile das Handy um einen frischen Stand, wenn der
 * letzte älter als eine Minute ist – so stimmt die Zeitzeile „Stand …“.
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
        val now = System.currentTimeMillis()
        val snapshot = loadSnapshot(settings)
        val screenWidthDp = requestParams.deviceParameters?.screenWidthDp ?: 192
        val screenHeightDp = requestParams.deviceParameters?.screenHeightDp ?: 192

        // Diagnose ohne Rätselraten: welchen Stand bekommt die Kachel und was
        // zeichnet sie davon? (Zeilen im Log gegen den Snapshot vergleichen)
        val plan = TileRenderer.layoutPlan(snapshot, screenHeightDp)
        Log.d(
            TAG,
            "Kachel: Fassung ${snapshot?.revision ?: -1}, ${plan.shown}/${plan.allowed} Zeilen" +
                " (gesamt ${plan.total}), sichtbar ${plan.visible}, gekuerzt ${plan.truncated}," +
                " Schrift ${plan.scale}, Stand ${settings.lastRefresh}, ${screenWidthDp}x${screenHeightDp} dp",
        )

        if (!pressedKey.isNullOrBlank()) {
            Log.d(TAG, "Button geklickt: $pressedKey")
            // Alte Fehlermeldung quittieren und das Handy schalten lassen
            settings.pressFailedAt = 0L
            Bridge.press(this, snapshot?.id, pressedKey)
        } else if (now - settings.lastRefresh > DISPLAY_REFRESH_MS) {
            // Handy um einen frischen Stand bitten (es überträgt ihn selbst)
            Bridge.refresh(this)
        }

        val layout = TileRenderer.render(
            this,
            snapshot,
            settings.lastRefresh,
            screenWidthDp,
            screenHeightDp,
            statusNote(settings, now, pressedKey != null),
        )

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

        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> {
        val builder = ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION)
        // Symbole für die Buttons bereitstellen (wie die Symbole am Handy)
        TileIcons.all.forEach { (id, drawableRes) ->
            builder.addIdToImageMapping(
                id,
                ResourceBuilders.ImageResource.Builder()
                    .setAndroidResourceByResId(
                        ResourceBuilders.AndroidImageResourceByResId.Builder()
                            .setResourceId(drawableRes)
                            .build()
                    )
                    .build()
            )
        }
        return Futures.immediateFuture(builder.build())
    }

    /** Kurze Statuszeile, wenn gerade etwas passiert oder noch Daten fehlen. */
    private fun statusNote(settings: Settings, now: Long, pressed: Boolean): String? {
        if (settings.pressFailedAt > 0L && now - settings.pressFailedAt < FAILED_HINT_MS) {
            return getString(R.string.tile_press_failed)
        }
        if (pressed) return getString(R.string.tile_pressing)
        if (settings.lastRefresh <= 0L) return getString(R.string.tile_waiting)
        return null
    }

    private fun loadSnapshot(settings: Settings): WidgetSnapshot? {
        val raw = settings.snapshotJson ?: return null
        return runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
        /**
         * Kennung der Tile-Ressourcen (Symbole). Bleibt konstant, weil sich die
         * Symbole mit der App-Version nicht ändern – muss aber **erhöht** werden,
         * sobald neue oder geänderte Symbole dazukommen, sonst zeigt die Uhr noch
         * die zwischengespeicherten alten Bilder.
         */
        private const val RESOURCES_VERSION = "2"

        /**
         * Wie lange die Kachel als „frisch“ gilt.
         *
         * Wear OS fragt die Kachel erst **nach** dieser Zeit wieder an – vorher
         * liefert es die zwischengespeicherte Darstellung aus. Mit den früheren
         * 15 Minuten wurde die Kachel beim Öffnen also gar nicht gefragt und
         * konnte deshalb auch nicht beim Handy nachfragen: Nur die App-Ansicht
         * (die beim Öffnen selbst anfragt) bekam neue Werte.
         *
         * Google empfiehlt, dafür höchstens einmal pro Minute zu aktualisieren.
         */
        private const val FRESHNESS_MILLIS = 60_000L

        /** Beim Anzeigen nachfragen, wenn der Stand älter ist als das hier. */
        private const val DISPLAY_REFRESH_MS = 60_000L

        /** So lange bleibt die Meldung „Druck fehlgeschlagen“ sichtbar. */
        private const val FAILED_HINT_MS = 20_000L
    }
}
