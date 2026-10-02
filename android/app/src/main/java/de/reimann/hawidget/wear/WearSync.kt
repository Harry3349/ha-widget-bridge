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

    /** Kurze Nachricht an die Uhr (z. B. „Druck fehlgeschlagen“). */
    fun sendToWatch(context: Context, path: String, payload: String = "") {
        runCatching {
            Wearable.getMessageClient(context)
                .sendMessage("", path, payload.toByteArray())
        }.onFailure { Log.w(TAG, "Nachricht an die Uhr fehlgeschlagen: ${it.message}") }
    }
}
