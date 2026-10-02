package de.reimann.hawidget.wear.bridge

import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.reimann.hawidget.wear.data.Bridge
import de.reimann.hawidget.wear.data.Settings

/**
 * Empfängt den Snapshot der Handy-App (und Meldungen über fehlgeschlagene
 * Tastendrücke) und zeichnet danach die Tile neu.
 */
class SnapshotListener : WearableListenerService() {

    override fun onDataChanged(events: DataEventBuffer) {
        try {
            for (event in events) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                if (event.dataItem.uri.path != Bridge.PATH_SNAPSHOT) continue

                val raw = event.dataItem.data?.toString(Charsets.UTF_8).orEmpty()
                if (raw.isBlank()) continue

                val settings = Settings(this)
                settings.snapshotJson = raw
                settings.lastRefresh = System.currentTimeMillis()
                Log.d(TAG, "Snapshot vom Handy erhalten (${raw.length} Zeichen)")
                Bridge.updateTile(this)
            }
        } finally {
            events.release()
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            // Eigene Fassung für diese Uhr (aus dem Smartwatch-Bereich der App)
            Bridge.PATH_SNAPSHOT -> {
                val raw = event.data?.toString(Charsets.UTF_8).orEmpty()
                if (raw.isBlank()) return
                val settings = Settings(this)
                settings.snapshotJson = raw
                settings.lastRefresh = System.currentTimeMillis()
                // Absender merken – dorthin gehen später „Button gedrückt“ usw.
                if (event.sourceNodeId.isNotBlank()) settings.phoneNode = event.sourceNodeId
                Log.d(TAG, "Snapshot für diese Uhr erhalten (${raw.length} Zeichen)")
                Bridge.updateTile(this)
            }

            Bridge.PATH_PRESS_FAILED -> {
                Log.w(TAG, "Handy meldet fehlgeschlagenen Tastendruck")
                Settings(this).pressFailedAt = System.currentTimeMillis()
                Bridge.updateTile(this)
            }

            else -> Unit
        }
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
    }
}
