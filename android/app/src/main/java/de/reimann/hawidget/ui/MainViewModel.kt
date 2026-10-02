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
                Widgets.refreshAllAsync(getApplication())
            }.onFailure { message = "Löschen fehlgeschlagen: ${it.message}" }
        }
    }

    // ---------------------------------------------------------------- Editor
    fun startNewWidget() {
        editor = WidgetDef(id = "", name = "")
        previewText = null
    }

    fun startEdit(def: WidgetDef) {
        editor = def
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

    /** Widgets, die für die Uhr gedacht sind. */
    fun watchWidgets(): List<WidgetDef> = widgets.filter { it.target != "phone" }

    /** Widgets, die als Vorlage taugen (alles außer reine Uhr-Fassungen). */
    fun copyableWidgets(): List<WidgetDef> = widgets.filter { !it.isWatchOnly }

    /**
     * Ein Handy-Widget als eigene Fassung für die Uhr kopieren und – wenn eine Uhr
     * gewählt ist – direkt dieser Uhr zuordnen.
     */
    fun copyToWatch(source: WidgetDef, nodeId: String?) {
        val client = clientOrNull() ?: return
        val copy = source.copy(
            id = "${source.id}_uhr".take(50),
            name = "${source.name} (Uhr)".take(80),
            target = "watch",
            watchNodes = listOfNotNull(nodeId?.takeIf { it.isNotBlank() }),
            revision = 0,
        )
        busy = true
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { client.saveWidget(copy) } }
            busy = false
            result.onSuccess { saved ->
                message = "„${saved.name}“ für die Uhr angelegt"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication())
            }.onFailure { message = "Kopieren fehlgeschlagen: ${it.message}" }
        }
    }

    /**
     * Festlegen, welche Fassung eine Uhr zeigt.
     *
     * Der Knoten wird aus allen anderen Uhr-Widgets entfernt, damit die
     * Zuordnung eindeutig bleibt. Ohne Angabe (null) gilt wieder das gemeinsame
     * Widget („Handy + Uhr“).
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
                Widgets.refreshAllAsync(getApplication())
            }.onFailure { message = "Zuordnen fehlgeschlagen: ${it.message}" }
        }
    }

    /** Zuordnung einer Uhr aufheben (zurück zum gemeinsamen Widget). */
    fun clearWatchAssignment(nodeId: String) {
        val client = clientOrNull() ?: return
        val assigned = widgets.filter { it.target == "watch" && it.watchNodes.contains(nodeId) }
        if (assigned.isEmpty()) {
            message = "Diese Uhr nutzt bereits das gemeinsame Widget"
            return
        }
        busy = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    assigned.forEach { widget ->
                        client.saveWidget(widget.copy(watchNodes = widget.watchNodes - nodeId))
                    }
                }
            }
            busy = false
            result.onSuccess {
                message = "Diese Uhr zeigt wieder das gemeinsame Widget"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication())
            }.onFailure { message = "Zurücksetzen fehlgeschlagen: ${it.message}" }
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
                message = "Widget „${saved.name}“ gespeichert"
                loadWidgets()
                Widgets.refreshAllAsync(getApplication())
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
