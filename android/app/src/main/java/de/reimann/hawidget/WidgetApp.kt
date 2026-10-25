package de.reimann.hawidget

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.widget.Widgets

/**
 * Prozessweiter Empfänger für das Einschalten des Bildschirms.
 *
 * Der Live-Dienst macht das selbst, solange er läuft. Ist er vom System beendet
 * worden, blieb das Widget bis zum nächsten 15-Minuten-Lauf stehen – nach dem
 * Entsperren wird jetzt sofort aktualisiert.
 */
class WidgetApp : Application() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val settings = Settings(this@WidgetApp)
                    if (settings.isConfigured) {
                        Widgets.refreshAllAsync(this@WidgetApp)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Selbstheilung: Planung nach Updates/Boot sicherstellen.
        Widgets.schedulePeriodicRefresh(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
