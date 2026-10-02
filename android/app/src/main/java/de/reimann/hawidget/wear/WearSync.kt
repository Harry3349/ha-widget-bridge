package de.reimann.hawidget.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable

/**
 * Brücke zur Uhr (Wearable Data Layer).
 *
 * Die Uhr hat meist kein WLAN, deshalb holt die Handy-App die Daten aus Home
 * Assistant und legt den Snapshot für die Uhr ab. Die Uhr schickt dafür nur
 * kurze Nachrichten (Button gedrückt / bitte aktualisieren).
 */
object WearSync {

    /** Muss mit der Uhr-App übereinstimmen. */
    const val PATH_SNAPSHOT = "/hawidget/snapshot"
    const val PATH_PRESS = "/hawidget/press"
    const val PATH_REFRESH = "/hawidget/refresh"

    private const val TAG = "HAWidgetBridge"

    /** Snapshot (JSON) an die Uhr übertragen. */
    fun pushSnapshot(context: Context, json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val request = PutDataRequest.create(PATH_SNAPSHOT)
                .setData(json.toByteArray())
            Wearable.getDataClient(context).putDataItem(request)
                .addOnSuccessListener { Log.d(TAG, "Snapshot an die Uhr übertragen") }
                .addOnFailureListener { Log.w(TAG, "Übertragen fehlgeschlagen: ${it.message}") }
        }.onFailure { Log.w(TAG, "Data Layer nicht verfügbar: ${it.message}") }
    }

    /**
     * Snapshot gezielt an *eine* Uhr schicken (Nachricht an ihren Knoten).
     *
     * So kann jede Uhr eine eigene Fassung zeigen; der Data Layer oben verteilt
     * einen Snapshot immer an alle Uhren.
     */
    fun pushSnapshotToNode(context: Context, nodeId: String, json: String?) {
        if (json.isNullOrBlank() || nodeId.isBlank()) return
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage(nodeId, PATH_SNAPSHOT, json.toByteArray())
        }.onSuccess { Log.d(TAG, "Snapshot an die Uhr $nodeId übertragen") }
            .onFailure { Log.w(TAG, "Übertragen an $nodeId fehlgeschlagen: ${it.message}") }
    }

    /**
     * Kurze Nachricht an die Uhr (z. B. „Druck fehlgeschlagen“).
     *
     * Mit Knoten-ID geht sie gezielt an eine Uhr; ohne geht sie an alle (leere
     * Knoten-ID liefert der Data Layer nicht zuverlässig aus).
     */
    fun sendToWatch(context: Context, path: String, payload: String = "", nodeId: String? = null) {
        runCatching {
            val client = Wearable.getMessageClient(context)
            if (nodeId.isNullOrBlank()) {
                client.sendMessage("", path, payload.toByteArray())
            } else {
                client.sendMessage(nodeId, path, payload.toByteArray())
            }
        }.onFailure { Log.w(TAG, "Nachricht an die Uhr fehlgeschlagen: ${it.message}") }
    }
}
