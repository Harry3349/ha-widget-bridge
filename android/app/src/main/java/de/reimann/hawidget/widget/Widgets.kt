package de.reimann.hawidget.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import de.reimann.hawidget.R
import de.reimann.hawidget.data.HaClient
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetJson
import de.reimann.hawidget.data.WidgetPrefs
import de.reimann.hawidget.data.WidgetSnapshot
import de.reimann.hawidget.wear.Watches
import de.reimann.hawidget.wear.WearSync
import de.reimann.hawidget.work.PressWorker
import de.reimann.hawidget.work.RefreshWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Zentrale Helfer rund um die Homescreen-Widgets. */
object Widgets {

    /** Wie oft höchstens geprüft wird, ob eine Uhr einen neuen Stand braucht. */
    private const val PUSH_CHECK_MS = 60_000L

    // ----------------------------------------------------------- Instanzen

    fun allIds(context: Context): List<Int> =
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HaWidgetProvider::class.java))
            .toList()

    fun update(context: Context, appWidgetId: Int, snapshot: WidgetSnapshot?, status: String?) {
        val views = WidgetRenderer.render(context, appWidgetId, snapshot, status)
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views)
    }

    fun cachedSnapshot(context: Context, widgetId: String): WidgetSnapshot? =
        WidgetPrefs.loadSnapshot(context, widgetId)?.let { raw ->
            runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
        }

    /**
     * Sofort eine Ansicht liefern, damit der Launcher nicht "Widget kann nicht
     * geladen werden" anzeigt. Danach folgt die Aktualisierung im Hintergrund.
     */
    fun initialView(context: Context, ids: List<Int>) {
        for (appWidgetId in ids) {
            val widgetId = WidgetPrefs.widgetId(context, appWidgetId)
            val snapshot = widgetId?.let { cachedSnapshot(context, it) }
            val status = when {
                widgetId == null -> context.getString(R.string.widget_placeholder)
                snapshot == null -> context.getString(R.string.widget_loading)
                else -> null
            }
            update(context, appWidgetId, snapshot, status)
        }
    }

    // ------------------------------------------------------ Aktualisieren

    /**
     * Aktualisierung anstoßen.
     *
     * ``force`` überspringt die Rücksicht auf den Bildschirmzustand – nötig nach
     * einer Änderung in der App: sonst kann der Job erst laufen, wenn der
     * Bildschirm schon aus ist, und die Uhr behält den alten Stand.
     */
    fun refreshAsync(
        context: Context,
        ids: List<Int>,
        reason: String = "manual",
        force: Boolean = false,
    ) {
        val targets = ids.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }
        if (targets.isEmpty() && !force) return

        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(
                workDataOf(
                    RefreshWorker.KEY_IDS to targets.toIntArray(),
                    RefreshWorker.KEY_FORCE to force,
                )
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("hawidget-refresh-$reason", ExistingWorkPolicy.REPLACE, request)
    }

    fun refreshAllAsync(context: Context, force: Boolean = false) =
        refreshAsync(context, allIds(context), "all", force)

    /** Alle übergebenen Widgets sofort aktualisieren (im Hintergrund-Thread aufrufen). */
    suspend fun refreshNow(
        context: Context,
        client: HaClient,
        ids: List<Int>,
        forceWatch: Boolean = false,
    ) {
        assignMissingWidgets(context, client, ids)

        val cache = HashMap<String, WidgetSnapshot?>()
        val raw = HashMap<String, String>()
        val failed = HashSet<String>()

        for (appWidgetId in ids) {
            val widgetId = WidgetPrefs.widgetId(context, appWidgetId)
            if (widgetId == null) {
                // In Home Assistant ist noch gar kein Widget angelegt
                update(context, appWidgetId, null, context.getString(R.string.widget_no_widget))
                continue
            }

            if (!cache.containsKey(widgetId)) {
                val json = runCatching { client.snapshotRaw(widgetId) }.getOrNull()
                if (json != null) {
                    WidgetPrefs.saveSnapshot(context, widgetId, json)
                    raw[widgetId] = json
                    cache[widgetId] = runCatching { WidgetJson.parseSnapshot(json) }.getOrNull()
                } else {
                    failed.add(widgetId)
                    cache[widgetId] = cachedSnapshot(context, widgetId)
                }
            }

            val snapshot = cache[widgetId]
            val status = when {
                widgetId in failed -> context.getString(R.string.widget_error)
                snapshot == null -> context.getString(R.string.widget_placeholder)
                else -> null
            }
            update(context, appWidgetId, snapshot, status)
        }

        pushToWatches(context, client, raw, forceWatch)
    }

    /**
     * Stand an die Uhren schicken – **nur wenn sich die Fassung geändert hat**.
     *
     * Der Data Layer kennt nur „an alle Uhren“; damit jede Uhr ihre eigene Fassung
     * zeigen kann, geht der Stand als Nachricht an genau den Knoten der Uhr. Jede
     * Nachricht weckt die Uhr (Funk + Schreiben + Kachel neu zeichnen), deshalb wird
     * im Live-Modus nicht mehr bei jedem Abruf gesendet: aktuelle Werte holt sich die
     * Uhr selbst, wenn ihre Kachel sichtbar wird. Nur eine geänderte Definition
     * (Revision) wird sofort übertragen – oder ``force`` nach einer Aktion in der App.
     */
    private suspend fun pushToWatches(
        context: Context,
        client: HaClient,
        known: Map<String, String>,
        force: Boolean = false,
    ) {
        val raw = HashMap(known)

        /** Stand eines Widgets – möglichst aus dem gerade geholten Abruf. */
        suspend fun rawOf(widgetId: String): String? = raw[widgetId] ?: withContext(Dispatchers.IO) {
            runCatching { client.snapshotRaw(widgetId) }.getOrNull()?.also { json ->
                WidgetPrefs.saveSnapshot(context, widgetId, json)
                raw[widgetId] = json
            }
        }

        // Der Live-Modus ruft das hier alle paar Sekunden auf. Für die Uhr genügt
        // eine Prüfung pro Minute – nach einer Änderung in der App (``force``) und
        // auf Anfrage der Uhr (die ihren Stand selbst anfordert) aber sofort.
        val now = System.currentTimeMillis()
        if (!force && now - WidgetPrefs.lastPushCheck(context) < PUSH_CHECK_MS) return
        WidgetPrefs.setLastPushCheck(context, now)

        val widgets = withContext(Dispatchers.IO) {
            runCatching { client.listWidgets() }.getOrNull()
        } ?: return

        // Nur Uhr-Fassungen kommen auf die Uhr – das Handy-Widget bleibt am Handy.
        val watchWidgets = widgets.filter { it.isWatchOnly }
        if (watchWidgets.isEmpty()) return

        // Schon alles übertragen? Dann weder Bluetooth abfragen noch die Uhr wecken.
        // Genau das spart den Akku: im Live-Modus ändern sich nur die Werte, die
        // Fassung (Revision) bleibt gleich.
        val pending = watchWidgets.filter { widget ->
            force || WidgetPrefs.lastPushedDefinition(context, widget.id) != widget.revision.toString()
        }
        if (pending.isEmpty()) return

        val forAll = watchWidgets.firstOrNull { it.watchNodes.isEmpty() }
        val nodes = withContext(Dispatchers.IO) { Watches.connected(context) }

        if (nodes.isEmpty()) {
            // Keine Uhr verbunden: die Änderung als „Data Item“ ablegen – eine später
            // verbundene Uhr bekommt sie damit, ohne dass wir mehrfach senden.
            pending.firstOrNull { it.watchNodes.isEmpty() }?.let { fallback ->
                rawOf(fallback.id)?.let { json ->
                    WearSync.pushSnapshot(context, json)
                    WidgetPrefs.setLastPushedDefinition(
                        context,
                        fallback.id,
                        fallback.revision.toString(),
                    )
                }
            }
            return
        }

        for (node in nodes) {
            val chosen = watchWidgets.firstOrNull { it.watchNodes.contains(node.id) } ?: forAll
                ?: continue
            if (chosen !in pending) continue

            rawOf(chosen.id)?.let { json ->
                WearSync.pushSnapshotToNode(context, node.id, json)
                WidgetPrefs.setLastPushedDefinition(
                    context,
                    chosen.id,
                    chosen.revision.toString(),
                )
            }
        }
    }

    /**
     * Homescreen-Widgets ohne Zuordnung übernehmen das erste in Home Assistant
     * angelegte Widget. Nötig, weil beim Anheften (``requestPinAppWidget``) die
     * Konfigurations-Activity nicht immer läuft – ohne Zuordnung würde das Widget
     * leer bleiben („Widget kann nicht geladen werden“).
     */
    private suspend fun assignMissingWidgets(
        context: Context,
        client: HaClient,
        ids: List<Int>,
    ) {
        val missing = ids.filter { WidgetPrefs.widgetId(context, it) == null }
        if (missing.isEmpty()) return

        // Nur Widgets, die auch fürs Handy gedacht sind (keine reinen Uhr-Fassungen)
        val first = runCatching {
            client.listWidgets().firstOrNull { !it.isWatchOnly }?.id
        }.getOrNull() ?: return
        missing.forEach { WidgetPrefs.setWidgetId(context, it, first) }
    }

    fun schedulePeriodicRefresh(context: Context) {
        val minutes = Settings(context).refreshMinutes.toLong().coerceAtLeast(15L)
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(minutes, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                "hawidget-periodic",
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
    }

    // ---------------------------------------------------------- Buttons

    fun press(context: Context, appWidgetId: Int, buttonKey: String) {
        val request = OneTimeWorkRequestBuilder<PressWorker>()
            .setInputData(
                workDataOf(
                    PressWorker.KEY_APPWIDGET_ID to appWidgetId,
                    PressWorker.KEY_BUTTON_KEY to buttonKey,
                )
            )
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
