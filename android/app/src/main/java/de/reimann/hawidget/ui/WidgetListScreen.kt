package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.data.WidgetDef

/**
 * Duplizieren: Vorlage und Ziel (Handy oder Uhr).
 */
private data class DuplicateRequest(
    val source: WidgetDef? = null,
    val target: String = "watch",
)

/**
 * Übersicht über alle Widgets – **klar getrennt** nach Handy und Smartwatch.
 *
 * * **Handy:** Widgets, die auf dem Homescreen liegen.
 * * **Smartwatch:** je verbundene Uhr die Auswahl, welche Fassung sie zeigt, und
 *   darunter die angelegten Uhr-Fassungen.
 *
 * Über **Duplizieren** entsteht aus jedem Widget eine Kopie – wahlweise als Handy-
 * oder als Smartwatch-Widget.
 */
@Composable
fun WidgetListScreen(
    vm: MainViewModel,
    onEdit: () -> Unit,
    onAddToHomeScreen: () -> Unit,
) {
    var duplicate by remember { mutableStateOf<DuplicateRequest?>(null) }

    // Die Uhren einmal abfragen (Bluetooth braucht einen Moment).
    LaunchedEffect(Unit) { if (vm.watches.isEmpty()) vm.loadWatches() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Widgets", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Handy und Smartwatch sind getrennte Bereiche: Ein Widget gehört entweder auf " +
                "den Homescreen oder auf die Uhr. Mit „Duplizieren“ machst du aus einem " +
                "Widget eine Kopie für die andere Seite.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                vm.startNewWidget()
                onEdit()
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Neu")
            }
            OutlinedButton(onClick = {
                vm.applyPreset()
                onEdit()
            }) {
                Text("Vorlage Shelly")
            }
        }

        if (vm.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

        // ------------------------------------------------------------- Handy
        SectionTitle("Handy (Homescreen)")
        Text(
            "Diese Widgets erscheinen auf dem Homescreen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(onClick = onAddToHomeScreen, modifier = Modifier.fillMaxWidth()) {
            Text("Widget zum Homescreen hinzufügen")
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Eingerichtete Homescreen-Widgets: ${vm.instanceCount}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.refreshWidgets() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Alle Widgets aktualisieren")
            }
        }

        val phoneWidgets = vm.phoneWidgets()
        if (phoneWidgets.isEmpty() && !vm.busy) {
            Text(
                "Noch kein Handy-Widget. „Vorlage Shelly“ füllt ein passendes Widget für die " +
                    "drei Shellys, beide USB-C-Leistungen und die Außentemperatur.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        phoneWidgets.forEach { def ->
            WidgetCard(
                title = def.name,
                subtitle = summary(def),
                note = null,
                onEdit = {
                    vm.startEdit(def)
                    onEdit()
                },
                onDuplicate = { duplicate = DuplicateRequest(def, oppositeOf(def)) },
                onDelete = { vm.deleteWidget(def.id) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        // -------------------------------------------------------- Smartwatch
        SectionTitle("Smartwatch (Uhr)")
        Text(
            "Jede Uhr zeigt eine eigene Fassung. Eine Uhr hat immer genau eine Fassung – " +
                "ohne Zuordnung bleibt die Uhr leer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (vm.watches.isEmpty()) {
            Text(
                if (vm.watchesLoading) "Uhren werden gesucht …"
                else "Keine Uhr gefunden. Die Uhr muss per Bluetooth verbunden und die " +
                    "Wear-App dort einmal geöffnet worden sein.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        vm.watches.forEach { watch ->
            WatchCard(
                watch = watch,
                widgets = vm.watchWidgets(),
                onAssign = { widget -> vm.assignWatchWidget(widget, watch.id) },
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.loadWatches() }, enabled = !vm.watchesLoading) {
                Text("Uhren suchen")
            }
            if (vm.watchesLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        OutlinedButton(
            onClick = { duplicate = DuplicateRequest(null, "watch") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Uhr-Fassung aus einem Handy-Widget anlegen")
        }

        val watchWidgets = vm.watchWidgets()
        if (watchWidgets.isEmpty() && !vm.busy) {
            Text(
                "Noch keine eigene Fassung für die Uhr – die Uhren zeigen das gemeinsame Widget.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        watchWidgets.forEach { def ->
            WidgetCard(
                title = def.name,
                subtitle = summary(def),
                note = "Verwendet auf: " + watchesLabel(vm, def),
                onEdit = {
                    vm.startEdit(def)
                    onEdit()
                },
                onDuplicate = { duplicate = DuplicateRequest(def, "watch") },
                onDelete = { vm.deleteWidget(def.id) },
            )
        }
    }

    // ------------------------------------------------------ Duplizieren-Dialog
    duplicate?.let { request ->
        DuplicateDialog(
            vm = vm,
            request = request,
            onDismiss = { duplicate = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge)
}

/** Eine Zeile der Übersicht mit den drei Aktionen. */
@Composable
private fun WidgetCard(
    title: String,
    subtitle: String,
    note: String?,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDuplicate) { Text("Duplizieren") }
                TextButton(onClick = onDelete) {
                    Text("Löschen", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(
    vm: MainViewModel,
    request: DuplicateRequest,
    onDismiss: () -> Unit,
) {
    var target by remember { mutableStateOf(request.target) }
    var sourceId by remember { mutableStateOf(request.source?.id ?: vm.widgets.firstOrNull()?.id) }
    val source = vm.widgets.firstOrNull { it.id == sourceId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Widget duplizieren") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Vorlage", style = MaterialTheme.typography.titleSmall)
                if (vm.widgets.isEmpty()) {
                    Text("Es ist noch kein Widget angelegt.")
                } else {
                    vm.widgets.forEach { def ->
                        FilterChip(
                            selected = def.id == sourceId,
                            onClick = { sourceId = def.id },
                            label = {
                                Text(
                                    "${def.name} · ${targetLabel(def)}",
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }

                Text("Die Kopie soll werden …", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = target == "phone",
                        onClick = { target = "phone" },
                        label = { Text("Handy-Widget") },
                    )
                    FilterChip(
                        selected = target == "watch",
                        onClick = { target = "watch" },
                        label = { Text("Smartwatch-Widget") },
                    )
                }
                Text(
                    if (target == "watch") {
                        "Die Kopie heißt „… (Uhr)“ und erscheint nur auf der Uhr – oben bei " +
                            "der Uhr ordnest du sie zu. Dort kannst du sie bearbeiten (Zeilen " +
                            "weglassen, Schrift größer stellen), ohne das Homescreen-Widget " +
                            "zu verändern."
                    } else {
                        "Die Kopie heißt „… (Handy)“ und erscheint nur auf dem Homescreen. " +
                            "Hefte sie danach über „Widget zum Homescreen hinzufügen“ an."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = source != null && vm.widgets.isNotEmpty(),
                onClick = {
                    source?.let { vm.duplicateWidget(it, target) }
                    onDismiss()
                },
            ) { Text("Duplizieren") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/** Kurzbeschreibung eines Widgets für die Übersicht. */
private fun summary(def: WidgetDef): String = buildString {
    append("id: ${def.id} · ")
    if (def.rows.isNotEmpty()) append("${def.rows.size} Zeilen · ")
    append("${def.values.size} Werte · ${def.buttons.size} Buttons · Revision ${def.revision}")
}

/**
 * Auf welchen Uhren diese Fassung liegt: Namen der zugeordneten Uhren oder
 * „alle Uhren“, wenn die Fassung keinem Knoten fest zugeordnet ist.
 */
private fun watchesLabel(vm: MainViewModel, def: WidgetDef): String {
    if (def.watchNodes.isEmpty()) return "alle Uhren"
    return def.watchNodes.joinToString(", ") { node -> watchName(vm, node) }
}

/** Name der Uhr zur Knoten-ID – die ID selbst, wenn sie (noch) nicht gefunden wurde. */
internal fun watchName(vm: MainViewModel, nodeId: String): String =
    vm.watches.firstOrNull { it.id == nodeId }?.name ?: nodeId

/** Kurztext, wo ein Widget liegt. */
internal fun targetLabel(widget: WidgetDef): String = if (widget.isWatchOnly) "nur Uhr" else "nur Handy"

/**
 * Die jeweils andere Seite – die Kopie soll in der Regel dorthin wandern, wo das
 * Widget noch nicht liegt (Handy-Widget → Uhr-Fassung und umgekehrt).
 */
private fun oppositeOf(widget: WidgetDef): String = if (widget.isWatchOnly) "phone" else "watch"
