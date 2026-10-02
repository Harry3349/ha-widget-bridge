package de.reimann.hawidget.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.TimeUnit

/** Eine verbundene Uhr (Wear-Knoten). */
data class WatchNode(
    val id: String,
    val name: String,
    val nearby: Boolean,
)

/**
 * Verbundene Uhren über den Wearable Data Layer.
 *
 * Ein Snapshot lässt sich über den Data Layer nur an *alle* Uhren verteilen.
 * Damit jede Uhr ihre eigene Fassung bekommt, schickt die Handy-App den
 * Snapshot als Nachricht an genau ihren Knoten.
 */
object Watches {

    private const val TAG = "HAWidgetBridge"

    /** Bluetooth braucht einen Moment – deshalb mit Zeitlimit arbeiten. */
    private const val TIMEOUT_MS = 8_000L

    /** Verbundene Uhren. Muss im Hintergrund-Thread aufgerufen werden. */
    fun connected(context: Context): List<WatchNode> = runCatching {
        val nodes = Tasks.await(
            Wearable.getNodeClient(context).connectedNodes,
            TIMEOUT_MS,
            TimeUnit.MILLISECONDS,
        )
        nodes.map { node ->
            WatchNode(
                id = node.id,
                name = node.displayName?.takeIf { it.isNotBlank() } ?: "Uhr",
                nearby = node.isNearby,
            )
        }
    }.onFailure { Log.w(TAG, "Uhren konnten nicht gelesen werden: ${it.message}") }
        .getOrDefault(emptyList())
}
