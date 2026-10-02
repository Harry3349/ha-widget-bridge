package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.data.WidgetDef
import de.reimann.hawidget.wear.WatchNode

/**
 * Smartwatch-Verwaltung: welche Fassung zeigt welche Uhr?
 *
 * Die Uhren werden über den Wearable Data Layer gefunden (Bluetooth). Für die Uhr
 * lässt sich eine eigene Fassung anlegen, indem ein Handy-Widget kopiert wird –
 * dort kann man dann Zeilen weglassen oder die Schrift größer stellen, ohne das
 * Homescreen-Widget zu verändern.
 */
@Composable
fun WatchScreen(
    vm: MainViewModel,
    onBack: () -> Unit,
    onEdit: (WidgetDef) -> Unit,
) {
    var copyDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadWatches() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Zurück") }
            Spacer(Modifier.width(8.dp))
            Text("Smartwatches", style = MaterialTheme.typography.headlineSmall)
        }

        Text(
            "Hier legst du fest, welches Widget auf welcher Uhr erscheint. Eine eigene " +
                "Fassung für die Uhr entsteht, indem du ein Handy-Widget kopierst – dort " +
                "kannst du dann Zeilen weglassen oder größer stellen. Ohne eigene Fassung " +
                "zeigt die Uhr das Widget „Handy + Uhr“.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.loadWatches() }, enabled = !vm.watchesLoading) {
                Text("Uhren suchen")
            }
            if (vm.watchesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        if (vm.watches.isEmpty()) {
            Text(
                "Keine Uhr gefunden. Die Uhr muss per Bluetooth mit dem Handy verbunden " +
                    "und die Wear-App dort einmal geöffnet worden sein.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        vm.watches.forEach { watch ->
            WatchCard(
                watch = watch,
                widgets = vm.watchWidgets(),
                onAssign = { widget -> vm.assignWatchWidget(widget, watch.id) },
                onClear = { vm.clearWatchAssignment(watch.id) },
                onCopy = { copyDialog = watch.id },
                onEdit = onEdit,
            )
        }

        HorizontalDivider()

        Text("Fassungen für die Uhr", style = MaterialTheme.typography.titleMedium)
        Text(
            "Diese Widgets sind (auch) für die Uhr gedacht.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        vm.watchWidgets().forEach { widget ->
            ListRow(
                title = widget.name,
                subtitle = "id: ${widget.id} · " + targetLabel(widget) +
                    (if (widget.watchNodes.isEmpty()) "" else " · ${widget.watchNodes.size} Uhr(en)"),
                onEdit = { onEdit(widget) },
                onDelete = { vm.deleteWidget(widget.id) },
            )
        }

        OutlinedButton(onClick = { copyDialog = "" }) {
            Text("Widget vom Handy kopieren")
        }
    }

    // Auswahl der Vorlage für die Kopie
    copyDialog?.let { nodeId ->
        val options = vm.copyableWidgets()
        AlertDialog(
            onDismissRequest = { copyDialog = null },
            title = { Text("Handy-Widget kopieren") },
            text = {
                if (options.isEmpty()) {
                    Text("Es ist noch kein Widget angelegt.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Welches Widget soll als Fassung für die Uhr angelegt werden?",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        options.forEach { widget ->
                            TextButton(
                                onClick = {
                                    vm.copyToWatch(widget, nodeId.ifBlank { null })
                                    copyDialog = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(widget.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { copyDialog = null }) { Text("Abbrechen") }
            },
        )
    }
}

/** Eine verbundene Uhr mit der Auswahl ihrer Fassung. */
@Composable
private fun WatchCard(
    watch: WatchNode,
    widgets: List<WidgetDef>,
    onAssign: (WidgetDef) -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onEdit: (WidgetDef) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val assigned = widgets.firstOrNull { it.target == "watch" && it.watchNodes.contains(watch.id) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(watch.name, style = MaterialTheme.typography.titleMedium)
            Text(
                (if (watch.nearby) "verbunden · " else "nicht in Reichweite · ") + watch.id,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                if (assigned == null) {
                    "Zeigt das gemeinsame Widget (Handy + Uhr)."
                } else {
                    "Zeigt: ${assigned.name}"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { open = true }) {
                        Text("Fassung wählen")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        DropdownMenuItem(
                            text = { Text("Gemeinsames Widget (Handy + Uhr)") },
                            onClick = {
                                onClear()
                                open = false
                            },
                        )
                        widgets.forEach { widget ->
                            DropdownMenuItem(
                                text = { Text(widget.name) },
                                onClick = {
                                    onAssign(widget)
                                    open = false
                                },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onCopy) { Text("Vom Handy kopieren") }
            }

            if (assigned != null) {
                TextButton(onClick = { onEdit(assigned) }) { Text("Fassung bearbeiten") }
            }
        }
    }
}

internal fun targetLabel(widget: WidgetDef): String = when (widget.target) {
    "watch" -> "nur Uhr"
    "phone" -> "nur Handy"
    else -> "Handy + Uhr"
}
