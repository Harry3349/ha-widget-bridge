package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.data.WidgetDef
import de.reimann.hawidget.wear.WatchNode

/**
 * Eine verbundene Uhr mit der Auswahl ihrer Fassung.
 *
 * Wird in der Widget-Übersicht unter „Smartwatch (Uhr)“ angezeigt: Hier wird
 * eingestellt, welches der verfügbaren Widgets diese Uhr verwendet.
 */
@Composable
internal fun WatchCard(
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
                OutlinedButton(onClick = onCopy) { Text("Kopie anlegen") }
            }

            if (assigned != null) {
                TextButton(onClick = { onEdit(assigned) }) { Text("Fassung bearbeiten") }
            }
        }
    }
}
