package de.reimann.hawidget.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import de.reimann.hawidget.R
import de.reimann.hawidget.data.Settings
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithNotification(getString(R.string.live_notification_title))

        if (!running) {
            running = true
            refreshJob = scope.launch { refreshLoop() }
            connect()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { socket?.close(1000, null) }
        refreshJob?.cancel()
        scope.cancel()
        super.onDestroy()
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
            if (running) connect()
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
