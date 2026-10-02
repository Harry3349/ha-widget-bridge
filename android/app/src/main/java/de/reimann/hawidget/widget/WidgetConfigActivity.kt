package de.reimann.hawidget.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.MainActivity
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.data.WidgetDef
import de.reimann.hawidget.data.WidgetPrefs
import de.reimann.hawidget.ui.HaWidgetBridgeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wird beim Ablegen des Widgets auf dem Homescreen gezeigt: Auswahl, welches
 * in Home Assistant gespeicherte Widget dargestellt werden soll.
 */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val settings by lazy { Settings(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setContent {
            HaWidgetBridgeTheme {
                var loading by remember { mutableStateOf(true) }
                var error by remember { mutableStateOf<String?>(null) }
                var widgets by remember { mutableStateOf<List<WidgetDef>>(emptyList()) }

                LaunchedEffect(Unit) {
                    if (!settings.isConfigured) {
                        loading = false
                        error = "Bitte zuerst die App einrichten (Server-URL und Token)."
                        return@LaunchedEffect
                    }
                    val result = withContext(Dispatchers.IO) {
                        runCatching { settings.client().listWidgets() }
                    }
                    loading = false
                    // Reine Uhr-Fassungen gehören nicht auf den Homescreen
                    result.onSuccess { all -> widgets = all.filter { !it.isWatchOnly } }
                        .onFailure { error = it.message ?: "Unbekannter Fehler" }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier
                            .safeDrawingPadding()
                            .padding(20.dp),
                    ) {
                        Text("Widget auswählen", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(12.dp))

                        when {
                            loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Lade Widgets …")
                            }

                            error != null -> Column {
                                Text(
                                    error.orEmpty(),
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row {
                                    Button(onClick = { openApp() }) { Text("App öffnen") }
                                    Spacer(Modifier.width(8.dp))
                                    TextButton(onClick = { finish() }) { Text("Abbrechen") }
                                }
                            }

                            widgets.isEmpty() -> Column {
                                Text("Es sind noch keine Widgets angelegt.")
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = { openApp() }) { Text("App öffnen") }
                            }

                            else -> {
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(widgets) { def ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                                .clickable { choose(def.id) },
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Text(
                                                    def.name,
                                                    style = MaterialTheme.typography.titleMedium,
                                                )
                                                Text(
                                                    "${def.values.size} Werte · " +
                                                        "${def.buttons.size} Buttons",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                TextButton(onClick = { finish() }) { Text("Abbrechen") }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(widgetId: String) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        WidgetPrefs.setWidgetId(this, appWidgetId, widgetId)
        Widgets.refreshAsync(this, listOf(appWidgetId), "config")
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
