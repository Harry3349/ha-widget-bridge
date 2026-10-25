package de.reimann.hawidget.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.widget.Widgets

/**
 * Startet nach Neustart oder App-Update, was sonst nur beim Öffnen der App
 * passiert: periodische Aktualisierung planen, einmal aktualisieren und – wenn
 * eingeschaltet – den Live-Dienst wieder hochfahren.
 *
 * Ohne diesen Empfänger blieb das Widget nach einem Reboot stehen, bis der
 * Nutzer die App öffnete.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_USER_UNLOCKED
        ) {
            return
        }

        Log.d(TAG, "Start nach $action – Aktualisierung neu aufsetzen")
        Widgets.schedulePeriodicRefresh(context)

        val settings = Settings(context)
        if (!settings.isConfigured) return

        // force = true: der Bildschirm kann beim Boot noch aus sein, das Widget
        // soll aber trotzdem mit aktuellen Werten bereitstehen.
        Widgets.refreshAllAsync(context, force = true)

        if (settings.liveMode) {
            runCatching { LiveUpdateService.start(context) }
                .onFailure { Log.w(TAG, "Live-Dienst nach $action nicht gestartet: ${it.message}") }
        }
    }

    private companion object {
        const val TAG = "HAWidgetBridge"
    }
}
