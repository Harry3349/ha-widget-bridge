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
            WearSync.PATH_PRESS -> handlePress(String(event.data))
            WearSync.PATH_REFRESH -> handleRefresh()
            else -> Unit
        }
    }

    /** Button drücken und danach den neuen Stand an die Uhr schicken. */
    private fun handlePress(buttonKey: String) {
        if (buttonKey.isBlank()) return
        val context = applicationContext
        val settings = Settings(context)
        if (!settings.isConfigured) {
            WearSync.sendToWatch(context, PATH_PRESS_FAILED)
            return
        }

        runBlocking {
            val client = settings.client()
            val widgetId = resolveWidgetId(client) ?: run {
                WearSync.sendToWatch(context, PATH_PRESS_FAILED)
                return@runBlocking
            }
            try {
                client.press(widgetId, buttonKey)
                Log.d(TAG, "Button '$buttonKey' gedrückt")
                // Kurz warten, damit Home Assistant den neuen Zustand meldet
                delay(400)
                val raw = client.snapshotRaw(widgetId)
                WidgetPrefs.saveSnapshot(context, widgetId, raw)
                WearSync.pushSnapshot(context, raw)
            } catch (error: Exception) {
                Log.w(TAG, "Druck fehlgeschlagen: ${error.message}")
                WearSync.sendToWatch(context, PATH_PRESS_FAILED)
            }
        }
    }

    /** Snapshot neu holen und an die Uhr schicken (z. B. beim Anzeigen der Tile). */
    private fun handleRefresh() {
        val context = applicationContext
        val settings = Settings(context)
        if (!settings.isConfigured) return

        runBlocking {
            val client = settings.client()
            val widgetId = resolveWidgetId(client) ?: return@runBlocking
            runCatching { client.snapshotRaw(widgetId) }
                .onSuccess { raw ->
                    WidgetPrefs.saveSnapshot(context, widgetId, raw)
                    WearSync.pushSnapshot(context, raw)
                }
                .onFailure { Log.w(TAG, "Aktualisieren fehlgeschlagen: ${it.message}") }
        }
    }

    /** Die Uhr zeigt dasselbe Widget wie die Handy-Widgets: das erste aus HA. */
    private suspend fun resolveWidgetId(client: HaClient): String? =
        runCatching { client.listWidgets().firstOrNull()?.id }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        private const val TAG = "HAWidgetBridge"
        private const val PATH_PRESS_FAILED = "/hawidget/press_failed"
    }
}
