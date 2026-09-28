package de.reimann.hawidget.widget

import android.content.Context
import android.os.PowerManager

/**
 * Sichtbarkeit des Widgets.
 *
 * Ein Homescreen-Widget ist nur bei eingeschaltetem Bildschirm zu sehen – das ist
 * das verlässlichste Signal, das eine App ohne Zusatzrechte bekommt (einen
 * „Widget ist sichtbar“-Callback gibt es in Android nicht). Aktualisierungen
 * laufen deshalb nur, solange der Bildschirm an ist.
 */
object ScreenState {

    fun isVisible(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        // Ohne PowerManager lieber aktualisieren als gar nichts tun
        return power?.isInteractive ?: true
    }
}
