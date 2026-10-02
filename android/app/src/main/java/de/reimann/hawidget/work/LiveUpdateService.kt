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
    private val socketLock = Any()
    private var refreshJob: Job? = null
    private var watchdogJob: Job? = null
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
            watchdogJob = scope.launch { connectionWatchdog() }
        }
        // Nur verbinden, wenn das Widget sichtbar sein kann (sonst wartet der
        // Dienst auf SCREEN_ON). Mehrfache Aufrufe sind unschädlich.
        connect()

        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        runCatching { takeSocket()?.close(1000, null) }
        refreshJob?.cancel()
        watchdogJob?.cancel()
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
        val current = takeSocket()
        runCatching { current?.close(1000, null) }
    }

    private fun resumeForScreenOn() {
        if (!ScreenState.isVisible(this)) return
        Log.d(TAG, "Bildschirm an – Live-Verbindung wird aufgebaut")
        connect()
        // Einmal sofort aktualisieren, damit das Widget nicht mit alten Werten
        // erscheint, während die Events eintrudeln.
        changes.trySend(Unit)
    }

    // ------------------------------------------------------------ WebSocket

    /**
     * Verbindung aufbauen, falls keine besteht.
     *
     * Bewusst mehrfach aufrufbar: Wächter, Display-Ereignis und Dienststart
     * dürfen sich nicht gegenseitig überholen.
     */
    private fun connect() {
        if (!ScreenState.isVisible(this)) return

        val settings = Settings(this)
        if (!settings.isConfigured) return

        synchronized(socketLock) {
            if (socket != null) return
            val request = Request.Builder().url(settings.client().websocketUrl()).build()
            socket = http.newWebSocket(request, listener(settings))
        }
    }

    /** Feld leeren (unter Sperre) und die alte Verbindung zurückgeben. */
    private fun takeSocket(): WebSocket? = synchronized(socketLock) {
        val current = socket
        socket = null
        current
    }

    /**
     * Verbindung aus dem Feld entfernen – aber nur, wenn sie noch die aktuelle
     * ist. Sonst würde das Schließen einer alten Verbindung die neue verwerfen.
     */
    private fun dropSocket(webSocket: WebSocket) {
        synchronized(socketLock) {
            if (socket === webSocket) socket = null
        }
    }

    private fun listener(settings: Settings) = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (json.optString("type")) {
                "auth_required" -> webSocket.send(authMessage(settings.token))
                "auth_ok" -> {
                    webSocket.send(SUBSCRIBE_MESSAGE)
                    Log.d(TAG, "Live-Verbindung steht")
                    updateNotification(getString(R.string.live_status_connected))
                    // Sofort einmal aktualisieren: nach einem HA-Neustart bleibt das
                    // Widget sonst mit dem Fehlerhinweis stehen, obwohl der Server
                    // längst wieder antwortet (Events kommen erst nach dem Abo).
                    changes.trySend(Unit)
                }
                "event" -> changes.trySend(Unit)
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Live-Verbindung verloren: ${t.message}")
            dropSocket(webSocket)
            updateNotification(getString(R.string.live_status_reconnecting))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            dropSocket(webSocket)
        }
    }

    /**
     * Wächter für die Verbindung.
     *
     * Ohne ihn blieb das Widget nach einem Home-Assistant-Neustart dauerhaft auf
     * dem Fehlerstand: Der WebSocket scheiterte, das Feld ``socket`` blieb aber
     * gesetzt – dadurch verband sich der Dienst nie wieder.
     */
    private suspend fun connectionWatchdog() {
        while (running) {
            delay(RECONNECT_CHECK_MS)
            if (!ScreenState.isVisible(this)) continue
            connect()
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
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /** Zustand der Live-Verbindung in der Dauerbenachrichtigung zeigen. */
    private fun updateNotification(text: String) {
        runCatching { notificationManager().notify(NOTIFICATION_ID, buildNotification(text)) }
    }

    private fun buildNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle(getString(R.string.live_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

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
        private const val RECONNECT_CHECK_MS = 15_000L
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
