package de.reimann.hawidget.ui

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.reimann.hawidget.data.HaClient
import de.reimann.hawidget.data.HaEntity
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetDef
import de.reimann.hawidget.data.WidgetJson
import de.reimann.hawidget.widget.HaWidgetProvider
import de.reimann.hawidget.widget.Widgets
import de.reimann.hawidget.wear.Watches
import de.reimann.hawidget.wear.WatchNode
import de.reimann.hawidget.work.LiveUpdateService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Zustand und Logik der App-Oberfläche. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = Settings(application)

    // Einrichtung
    var url by mutableStateOf(settings.baseUrl)
    var token by mutableStateOf(settings.token)
    var refreshMinutes by mutableStateOf(settings.refreshMinutes.toString())
    var liveMode by mutableStateOf(settings.liveMode)
    var connectionStatus by mutableStateOf("")

    // Daten
    var widgets by mutableStateOf<List<WidgetDef>>(emptyList())
    var entities by mutableStateOf<List<HaEntity>>(emptyList())
    var instanceCount by mutableStateOf(0)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)

    // Smartwatches
    /** Verbundene Uhren (Wearable Data Layer). */
    var watches by mutableStateOf<List<WatchNode>>(emptyList())
        private set
    var watchesLoading by mutableStateOf(false)
        private set

    // Editor
    var editor by mutableStateOf<WidgetDef?>(null)
    var previewText by mutableStateOf<String?>(null)

    /** Stand beim Öffnen des Editors – zum Erkennen ungespeicherter Änderungen. */
    private var editorSource: WidgetDef? = null

    init {
        if (settings.isConfigured) {
            loadWidgets()
            loadEntities()
            // Intervall und Live-Dienst auch nach App-Neustart sicherstellen
            Widgets.schedulePeriodicRefresh(application)
            if (settings.liveMode) {
                LiveUpdateService.start(application)
            }
            // Wer die App öffnet, schaut auch aufs Widget – einmal aktualisieren
            Widgets.refreshAllAsync(application)
        }
        updateInstanceCount()
    }

    // ------------------------------------------------------------ Einrichtung

    fun saveSettings() {
        settings.baseUrl = url
        settings.token = token
        settings.refreshMinutes = refreshMinutes.toIntOrNull() ?: 15
        settings.liveMode = liveMode
        refreshMinutes = settings.refreshMinutes.toString()

        Widgets.schedulePeriodicRefresh(getApplication())
        if (liveMode) {
            LiveUpdateService.start(getApplication())
        } else {
            LiveUpdateService.stop(getApplication())
        }
        connectionStatus = "Gespeichert."
    }

    fun testConnection() {
        val client = clientOrNull() ?: return
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.testConnection() } }
            busy = false
            connectionStatus = result.fold(
                onSuccess = { "Verbindung okay – $it" },
                onFailure = { "Fehler: ${it.message}" },
            )
        }
    }

    // --------------------------------------------------------------- Widgets

    fun loadWidgets() {
        val client = clientOrNull() ?: return
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.listWidgets() } }
            busy = false
            result.onSuccess { widgets = it }
                .onFailure { message = "Widgets laden fehlgeschlagen: ${it.message}" }
        }
    }

    fun loadEntities() {
        val client = clientOrNull() ?: return
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.entities() } }
            result.onSuccess { entities = it.sortedBy { entity -> entity.entityId } }
        }
    }

    fun deleteWidget(widgetId: String) {
        val client = clientOrNull() ?: return
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.deleteWidget(widgetId) } }
            busy = false
            result.onSuccess {
                message = "Widget „$widgetId“ gelöscht"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication(), force = true)
            }.onFailure { message = "Löschen fehlgeschlagen: ${it.message}" }
        }
    }

    // ---------------------------------------------------------------- Editor
    fun startNewWidget() {
        editor = WidgetDef(id = "", name = "")
        editorSource = editor
        previewText = null
    }

    fun startEdit(def: WidgetDef) {
        editor = def
        editorSource = def
        previewText = null
    }

    fun applyPreset() {
        val raw = runCatching {
            getApplication<Application>().assets.open(PRESET_ASSET)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()

        if (raw == null) {
            message = "Vorlage nicht gefunden"
            return
        }

        val parsed = runCatching { WidgetJson.parseWidget(JSONObject(raw)) }.getOrNull()
        if (parsed == null) {
            message = "Vorlage konnte nicht gelesen werden"
            return
        }

        // Als neues Widget öffnen: IDs vergibt Home Assistant beim Speichern
        editor = parsed.copy(id = "shelly", revision = 0)
        previewText = null
    }

    fun updateEditor(transform: (WidgetDef) -> WidgetDef) {
        val current = editor ?: return
        editor = transform(current)
    }

    /**
     * Wurde im Editor etwas geändert, das noch nicht gespeichert ist? Damit fragt
     * der Zurück-Weg nach, bevor Eingaben verloren gehen.
     */
    fun editorDirty(): Boolean {
        val current = editor ?: return false
        return current != editorSource
    }

    /** Editor verlassen (ohne zu speichern). */
    fun closeEditor() {
        editor = null
        editorSource = null
        previewText = null
    }

    // ---------------------------------------------------------- Smartwatches

    /** Verbundene Uhren abfragen (Bluetooth braucht einen Moment). */
    fun loadWatches() {
        watchesLoading = true
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Watches.connected(getApplication()) }
            watches = found
            watchesLoading = false
            if (found.isEmpty()) {
                message = "Keine Uhr gefunden – ist sie per Bluetooth mit dem Handy verbunden?"
            }
        }
    }

    /** Widgets für den Homescreen. */
    fun phoneWidgets(): List<WidgetDef> = widgets.filter { !it.isWatchOnly }

    /** Uhr-Fassungen – sie lassen sich einer Uhr zuordnen. */
    fun watchWidgets(): List<WidgetDef> = widgets.filter { it.isWatchOnly }

    /**
     * Ein Widget duplizieren – wahlweise als **Handy-Widget** (Homescreen) oder als
     * **Smartwatch-Widget** (Fassung für die Uhr).
     */
    fun duplicateWidget(source: WidgetDef, target: String) {
        val client = clientOrNull() ?: return
        val forWatch = target == "watch"

        busy = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    client.saveWidget(
                        source.copy(
                            id = uniqueId(source.id + if (forWatch) "_uhr" else "_handy"),
                            name = (source.name + if (forWatch) " (Uhr)" else " (Handy)").take(80),
                            target = if (forWatch) "watch" else "phone",
                            watchNodes = emptyList(),
                            revision = 0,
                        )
                    )
                }
            }
            busy = false
            result.onSuccess { saved ->
                message = if (forWatch) {
                    "„${saved.name}“ als Uhr-Widget angelegt"
                } else {
                    "„${saved.name}“ als Handy-Widget angelegt"
                }
                loadWidgets()
                Widgets.refreshAllAsync(getApplication(), force = true)
            }.onFailure { message = "Duplizieren fehlgeschlagen: ${it.message}" }
        }
    }

    /** Kennung, die noch nicht vergeben ist (Anhang ``_2``, ``_3`` …). */
    private fun uniqueId(base: String): String {
        val taken = widgets.map { it.id }.toSet()
        val trimmed = base.take(50)
        if (trimmed !in taken) return trimmed
        var suffix = 2
        while (true) {
            val candidate = trimmed.take(46) + "_" + suffix
            if (candidate !in taken) return candidate
            suffix++
        }
    }

    /**
     * Festlegen, welche Fassung eine Uhr zeigt.
     *
     * Der Knoten wird aus allen anderen Uhr-Fassungen entfernt, damit eine Uhr
     * immer genau eine zeigt.
     */
    fun assignWatchWidget(widget: WidgetDef, nodeId: String?) {
        val client = clientOrNull() ?: return
        if (nodeId.isNullOrBlank()) return

        busy = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    // Andere Widgets von dieser Uhr lösen
                    widgets.filter { other ->
                        other.id != widget.id && other.target == "watch" &&
                            other.watchNodes.contains(nodeId)
                    }.forEach { other ->
                        client.saveWidget(other.copy(watchNodes = other.watchNodes - nodeId))
                    }
                    // und dieses Widget dieser Uhr zuordnen
                    client.saveWidget(
                        widget.copy(watchNodes = (widget.watchNodes + nodeId).distinct())
                    )
                }
            }
            busy = false
            result.onSuccess {
                message = "„${widget.name}“ ist jetzt auf dieser Uhr"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication(), force = true)
            }.onFailure { message = "Zuordnen fehlgeschlagen: ${it.message}" }
        }
    }

    fun saveEditor() {
        val client = clientOrNull() ?: return
        val def = editor ?: return
        if (def.name.isBlank()) {
            message = "Bitte einen Namen vergeben"
            return
        }
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.saveWidget(def) } }
            busy = false
            result.onSuccess { saved ->
                editor = saved
                editorSource = saved
                message = "Widget „${saved.name}“ gespeichert"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication(), force = true)
            }.onFailure { message = "Speichern fehlgeschlagen: ${it.message}" }
        }
    }

    fun renderPreview() {
        val client = clientOrNull() ?: return
        val def = editor ?: return
        busy = true
        viewModelScope.launch {
            val safe = if (def.name.isBlank()) def.copy(name = "Vorschau") else def
            val result = runCatching { withContext(Dispatchers.IO) { client.preview(safe) } }
            busy = false
            result.onSuccess { snapshot ->
                // Werte in der eingestellten Spaltenzahl zeigen, damit die
                // Anordnung schon vor dem Speichern sichtbar ist.
                val lines = snapshot.previewLines()
                previewText = if (lines.isEmpty()) snapshot.text else lines.joinToString("\n")
            }.onFailure { message = "Vorschau fehlgeschlagen: ${it.message}" }
        }
    }

    // ------------------------------------------------------------- Homescreen

    fun updateInstanceCount() {
        instanceCount = Widgets.allIds(getApplication()).size
    }

    fun pinWidget() {
        val manager = AppWidgetManager.getInstance(getApplication())
        val provider = ComponentName(getApplication(), HaWidgetProvider::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.requestPinAppWidget(provider, null, null)
        }
        updateInstanceCount()
    }

    fun refreshWidgets() {
        Widgets.refreshAllAsync(getApplication())
        updateInstanceCount()
    }

    // --------------------------------------------------------------- intern

    private fun clientOrNull(): HaClient? {
        if (url.isBlank() || token.isBlank()) {
            message = "Bitte Server-URL und Token eintragen"
            return null
        }
        return HaClient(url, token)
    }

    companion object {
        private const val PRESET_ASSET = "preset_shelly.json"
    }
}
