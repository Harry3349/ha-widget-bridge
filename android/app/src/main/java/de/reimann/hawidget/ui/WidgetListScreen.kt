package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WidgetListScreen(
    vm: MainViewModel,
    onEdit: () -> Unit,
    onAddToHomeScreen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Widgets", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Diese Widgets sind in Home Assistant gespeichert und erscheinen dort als Gerät " +
                "mit Inhalts-Sensor und je einem Button pro Schaltfläche.",
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

        OutlinedButton(onClick = onAddToHomeScreen, modifier = Modifier.fillMaxWidth()) {
            Text("Widget zum Homescreen hinzufügen")
        }

        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Eingerichtete Homescreen-Widgets: ${vm.instanceCount}",
                style = MaterialTheme.typography.bodySmall,
            )
            IconButton(onClick = { vm.refreshWidgets() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Alle Widgets aktualisieren")
            }
        }

        if (vm.busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        vm.widgets.forEach { def ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(def.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "id: ${def.id} · ${def.values.size} Werte · ${def.buttons.size} Buttons · " +
                            (if (def.rows.isNotEmpty()) "${def.rows.size} Zeilen · " else "") +
                            "Revision ${def.revision}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row {
                        TextButton(onClick = {
                            vm.startEdit(def)
                            onEdit()
                        }) {
                            Text("Bearbeiten")
                        }
                        TextButton(onClick = { vm.deleteWidget(def.id) }) {
                            Text("Löschen", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        if (vm.widgets.isEmpty() && !vm.busy) {
            Text(
                "Noch keine Widgets angelegt. „Vorlage Shelly“ füllt ein passendes Widget " +
                    "für die drei Shellys, beide USB-C-Leistungen und die Außentemperatur.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
