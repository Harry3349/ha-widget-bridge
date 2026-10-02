package de.reimann.hawidget.wear

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import de.reimann.hawidget.data.HaClient
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetPrefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Empfängt Nachrichten der Uhr.
 *
 * Die Uhr hat meist kein WLAN: Sie schickt „Button gedrückt“ oder „bitte
 * aktualisieren“ an die Handy-App, die den Home-Assistant-Aufruf macht und den
 * neuen Snapshot zurücklegt.
 */
class WearMessageListener : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Nachricht von der Uhr: ${event.path}")
        when (event.path) {
            WearSync.PATH_PRESS -> handlePress(String(event.data), event.sourceNodeId)
            WearSync.PATH_REFRESH -> handleRefresh(event.sourceNodeId)
            else -> Unit
        }
    }

    /**
     * Button drücken und danach den neuen Stand an die Uhr schicken.
     *
     * Die Uhr schickt "<widget-id>|<button-key>"; ältere Fassungen nur den
     * Schlüssel – dann gilt weiterhin das erste Widget in Home Assistant.
     */
    private fun handlePress(payload: String, sourceNodeId: String?) {
        val parts = payload.split('|', limit = 2)
        val widgetId = if (parts.size == 2) parts[0].trim() else ""
        val buttonKey = parts.last().trim()
        if (buttonKey.isBlank()) return

        val context = applicationContext
        val settings = Settings(context)
        if (!settings.isConfigured) {
            WearSync.sendToWatch(context, PATH_PRESS_FAILED)
            return
        }

        runBlocking {
            val client = settings.client()
            val target = widgetId.takeIf { it.isNotBlank() } ?: resolveWidgetId(client)
            if (target == null) {
                WearSync.sendToWatch(context, PATH_PRESS_FAILED)
                return@runBlocking
            }
            try {
                client.press(target, buttonKey)
                Log.d(TAG, "Button '$buttonKey' in '$target' gedrückt")
                // Kurz warten, damit Home Assistant den neuen Zustand meldet
                delay(400)
                val raw = client.snapshotRaw(target)
                WidgetPrefs.saveSnapshot(context, target, raw)
                pushBack(context, sourceNodeId, raw)
            } catch (error: Exception) {
                Log.w(TAG, "Druck fehlgeschlagen: ${error.message}")
                WearSync.sendToWatch(context, PATH_PRESS_FAILED)
            }
        }
    }

    /** Snapshot neu holen und an die Uhr schicken (z. B. beim Anzeigen der Tile). */
    private fun handleRefresh(sourceNodeId: String?) {
        val context = applicationContext
        val settings = Settings(context)
        if (!settings.isConfigured) return

        runBlocking {
            val client = settings.client()
            val widgetId = resolveWidgetId(client) ?: return@runBlocking
            runCatching { client.snapshotRaw(widgetId) }
                .onSuccess { raw ->
                    WidgetPrefs.saveSnapshot(context, widgetId, raw)
                    pushBack(context, sourceNodeId, raw)
                }
                .onFailure { Log.w(TAG, "Aktualisieren fehlgeschlagen: ${it.message}") }
        }
    }

    /**
     * Neuen Stand zurückgeben: gezielt an die anfragende Uhr und zusätzlich über
     * den Data Layer, damit auch eine zweite Uhr den Stand bekommt.
     */
    private fun pushBack(context: android.content.Context, nodeId: String?, raw: String) {
        if (nodeId.isNullOrBlank()) {
            WearSync.pushSnapshot(context, raw)
            return
        }
        WearSync.pushSnapshotToNode(context, nodeId, raw)
    }

    /**
     * Die Uhr ohne eigene Zuordnung zeigt die gemeinsame Fassung: erst ein Widget
     * für die Uhr („Handy + Uhr“ bzw. „Uhr“), sonst das erste aus Home Assistant.
     */
    private suspend fun resolveWidgetId(client: HaClient): String? {
        val widgets = runCatching { client.listWidgets() }.getOrDefault(emptyList())
        val watchWidget = widgets.firstOrNull { it.target != "phone" }
        val id = watchWidget?.id ?: widgets.firstOrNull()?.id
        return id?.takeIf { it.isNotBlank() }
    }

    companion object {
        private const val TAG = "HAWidgetBridge"
        private const val PATH_PRESS_FAILED = "/hawidget/press_failed"
    }
}
