package de.reimann.hawidget.wear.data

import android.content.Context
import android.util.Log
import androidx.wear.tiles.TileService
import com.google.android.gms.wearable.Wearable
import de.reimann.hawidget.wear.tile.WidgetTileService

/**
 * Brücke zur Handy-App.
 *
 * Die Uhr hat meist kein WLAN: Sie zeigt den Snapshot an, den die Handy-App
 * über den Wearable Data Layer ablegt, und schickt nur kurze Nachrichten
 * („Button gedrückt“, „bitte aktualisieren“).
 */
object Bridge {

    /** Muss mit der Handy-App übereinstimmen. */
    const val PATH_SNAPSHOT = "/hawidget/snapshot"
    const val PATH_PRESS = "/hawidget/press"
    const val PATH_REFRESH = "/hawidget/refresh"
    const val PATH_PRESS_FAILED = "/hawidget/press_failed"

    private const val TAG = "HAWidgetBridge"

    fun press(context: Context, buttonKey: String) = send(context, PATH_PRESS, buttonKey)

    fun refresh(context: Context) = send(context, PATH_REFRESH, "")

    private fun send(context: Context, path: String, payload: String) {
        runCatching {
            Wearable.getMessageClient(context).sendMessage("", path, payload.toByteArray())
        }.onFailure { Log.w(TAG, "Nachricht an das Handy fehlgeschlagen: ${it.message}") }
    }

    /** Tile neu zeichnen lassen. */
    fun updateTile(context: Context) {
        runCatching { TileService.getUpdater(context).requestUpdate(WidgetTileService::class.java) }
            .onFailure { Log.w(TAG, "Tile-Aktualisierung fehlgeschlagen: ${it.message}") }
    }
}
