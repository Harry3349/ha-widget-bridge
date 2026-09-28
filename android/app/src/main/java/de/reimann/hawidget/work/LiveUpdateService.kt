package de.reimann.hawidget.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import de.reimann.hawidget.R
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.widget.ScreenState
import de.reimann.hawidget.widget.Widgets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

/**
 * Optionaler Live-Modus.
 *
 * Hält eine WebSocket-Verbindung zur HA-Instanz und aktualisiert die Widgets,
 * sobald sich ein Zustand ändert (gebündelt, damit nicht jeder Sensorwert einen
 * eigenen Request auslöst).
 */
class LiveUpdateService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val changes = Channel<Unit>(Channel.CONFLATED)
    private val http: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var refreshJob: Job? = null
    private var running = false
    private var screenReceiverRegistered = false

    /**
     * Live-Modus nur, solange das Widget sichtbar sein kann: bei ausgeschaltetem
     * Bildschirm wird die WebSocket-Verbindung getrennt (spart Funkmodul und
     * Akku), beim Einschalten sofort wieder verbunden und aktualisiert.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> pauseForScreenOff()
                Intent.ACTION_SCREEN_ON -> resumeForScreenOn()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
        )
        screenReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification(getString(R.string.live_notification_title))

        if (!running) {
            running = true
            refreshJob = scope.launch { refreshLoop() }
        }
        // Nur verbinden, wenn noch keine Verbindung besteht und das Widget
        // sichtbar sein kann (sonst wartet der Dienst auf SCREEN_ON)
        if (socket == null && ScreenState.isVisible(this)) {
            connect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { socket?.close(1000, null) }
        socket = null
        refreshJob?.cancel()
        if (screenReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            screenReceiverRegistered = false
        }
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------- Sichtbarkeit

    private fun pauseForScreenOff() {
        Log.d(TAG, "Bildschirm aus – Live-Verbindung wird getrennt")
        runCatching { socket?.close(1000, null) }
        socket = null
    }

    private fun resumeForScreenOn() {
        if (!ScreenState.isVisible(this)) return
        Log.d(TAG, "Bildschirm an – Live-Verbindung wird aufgebaut")
        if (socket == null) connect()
        // Einmal sofort aktualisieren, damit das Widget nicht mit alten Werten
        // erscheint, während die Events eintrudeln.
        changes.trySend(Unit)
    }

    // ------------------------------------------------------------ WebSocket

    private fun connect() {
        val settings = Settings(this)
        if (!settings.isConfigured) return

        val request = Request.Builder().url(settings.client().websocketUrl()).build()

        socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                    when (json.optString("type")) {
                        "auth_required" -> webSocket.send(authMessage(settings.token))
                        "auth_ok" -> webSocket.send(SUBSCRIBE_MESSAGE)
                        "event" -> changes.trySend(Unit)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    scheduleReconnect()
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    scheduleReconnect()
                }
            },
        )
    }

    private fun authMessage(token: String): String =
        JSONObject().put("type", "auth").put("access_token", token).toString()

    private fun scheduleReconnect() {
        if (!running) return
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            // Bei ausgeschaltetem Bildschirm wartet der Dienst auf SCREEN_ON
            if (running && socket == null && ScreenState.isVisible(this@LiveUpdateService)) {
                connect()
            }
        }
    }

    // ----------------------------------------------------------- Aktualisieren

    private suspend fun refreshLoop() {
        while (true) {
            // Warten, bis Home Assistant eine Zustandsänderung meldet
            changes.receive()
            delay(DEBOUNCE_MS)
            while (changes.tryReceive().isSuccess) {
                // Weitere Ereignisse im selben Zeitfenster verwerfen
            }

            // Ohne sichtbares Widget nichts abrufen – das passiert z. B. direkt
            // nach dem Aufbau der Verbindung oder beim Einschalten des Displays.
            if (!ScreenState.isVisible(this)) continue

            val settings = Settings(this)
            if (!settings.isConfigured) continue

            runCatching {
                Widgets.refreshNow(this, settings.client(), Widgets.allIds(this))
            }
        }
    }

    // -------------------------------------------------------- Benachrichtigung

    private fun startForegroundWithNotification(text: String) {
        ensureChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle(getString(R.string.live_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val manager = notificationManager()
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.live_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.live_channel_description)
                setShowBadge(false)
            }
        )
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(NotificationManager::class.java)

    companion object {
        const val ACTION_STOP = "de.reimann.hawidget.action.LIVE_STOP"

        private const val TAG = "HAWidgetBridge"
        private const val CHANNEL_ID = "hawidget_live"
        private const val NOTIFICATION_ID = 4711
        private const val DEBOUNCE_MS = 1500L
        private const val RECONNECT_DELAY_MS = 15_000L
        private const val SUBSCRIBE_MESSAGE =
            "{\"id\":1,\"type\":\"subscribe_events\",\"event_type\":\"state_changed\"}"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, LiveUpdateService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveUpdateService::class.java))
        }
    }
}
