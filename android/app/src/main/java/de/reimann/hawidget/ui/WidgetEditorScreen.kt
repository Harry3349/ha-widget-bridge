package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun WidgetEditorScreen(vm: MainViewModel, onBack: () -> Unit) {
    val def = vm.editor ?: return

    var valueIndex by remember { mutableStateOf<Int?>(null) }
    var buttonIndex by remember { mutableStateOf<Int?>(null) }

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
            Text(
                if (def.isNew) "Neues Widget" else "Widget bearbeiten",
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        OutlinedTextField(
            value = def.name,
            onValueChange = { newName -> vm.updateEditor { current -> current.copy(name = newName) } },
            label = { Text("Name (erscheint als Gerätename in Home Assistant)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "ID: ${def.id.ifBlank { "wird aus dem Namen erzeugt" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = def.textSize.toInt().toString(),
            onValueChange = { text ->
                val size = text.filter(Char::isDigit).toFloatOrNull() ?: return@OutlinedTextField
                vm.updateEditor { current -> current.copy(textSize = size.coerceIn(8f, 30f)) }
            },
            label = { Text("Schriftgröße im Widget") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        // --------------------------------------------------------------- Werte
        Text("Werte", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ein Wert pro Feld. Die Reihenfolge hier ist die Reihenfolge im Widget " +
                "(erst von links nach rechts, dann die nächste Zeile).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        def.values.forEachIndexed { index, value ->
            ListRow(
                title = value.label ?: value.entity,
                subtitle = value.entity,
                onEdit = { valueIndex = index },
                onDelete = {
                    vm.updateEditor { current ->
                        current.copy(values = current.values.filterIndexed { i, _ -> i != index })
                    }
                },
            )
        }

        OutlinedButton(onClick = { valueIndex = NEW_ENTRY }) { Text("Wert hinzufügen") }

        // ----------------------------------------------------------- Anordnung
        Text("Anordnung der Werte", style = MaterialTheme.typography.titleMedium)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Nebeneinander", style = MaterialTheme.typography.bodyMedium)
            listOf(1, 2, 3).forEach { columns ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = def.valueColumns == columns,
                        onClick = {
                            vm.updateEditor { current -> current.copy(valueColumns = columns) }
                        },
                    )
                    Text(if (columns == 1) "1 Spalte" else "$columns Spalten")
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = def.valueLabelAbove,
                onCheckedChange = { checked ->
                    vm.updateEditor { current -> current.copy(valueLabelAbove = checked) }
                },
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Wert unter dem Namen", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Name in der ersten Zeile, Wert darunter – sonst beides in einer Zeile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        // -------------------------------------------------- Zeilen (Handy+Uhr)
        RowEditorSection(
            rows = def.rows,
            entities = vm.entities,
            onRowsChange = { rows -> vm.updateEditor { current -> current.copy(rows = rows) } },
        )

        HorizontalDivider()

        // ------------------------------------------------------------- Buttons
        Text("Buttons", style = MaterialTheme.typography.titleMedium)
        Text(
            "Jeder Button wird in Home Assistant als eigene Button-Entity angelegt " +
                "und lässt sich dort auch per Automation auslösen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        def.buttons.forEachIndexed { index, button ->
            ListRow(
                title = button.label,
                subtitle = "${button.service} · ${button.entityId ?: "ohne Entity"}",
                onEdit = { buttonIndex = index },
                onDelete = {
                    vm.updateEditor { current ->
                        current.copy(buttons = current.buttons.filterIndexed { i, _ -> i != index })
                    }
                },
            )
        }

        OutlinedButton(onClick = { buttonIndex = NEW_ENTRY }) { Text("Button hinzufügen") }

        HorizontalDivider()

        // ------------------------------------------------------------ Template
        Text("Eigenes Template (optional)", style = MaterialTheme.typography.titleMedium)
        Text(
            "Overlay: Ist ein Jinja-Template gesetzt, ersetzt es die Werteliste. " +
                "Erlaubt sind u. a. <b>, <i>, <font color='#00e676'>, <br>. " +
                "Beispiel: {{ '%.1f'|format(states('sensor.x') | float(0)) }} W",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = def.template.orEmpty(),
            onValueChange = { text ->
                vm.updateEditor { current -> current.copy(template = text.ifBlank { null }) }
            },
            label = { Text("Jinja-Template") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.renderPreview() }, enabled = !vm.busy) { Text("Vorschau") }
            Button(onClick = { vm.saveEditor() }, enabled = !vm.busy) { Text("Speichern") }
        }

        if (vm.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Wird gespeichert …", style = MaterialTheme.typography.bodySmall)
            }
        }

        vm.previewText?.let { text ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Vorschau (Klartext)", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    // ---------------------------------------------------------------- Dialoge
    valueIndex?.let { index ->
        ValueEditorDialog(
            initial = def.values.getOrNull(index),
            entities = vm.entities,
            onDismiss = { valueIndex = null },
            onConfirm = { updated ->
                vm.updateEditor { current ->
                    val values = current.values.toMutableList()
                    if (index == NEW_ENTRY || index >= values.size) {
                        values.add(updated)
                    } else {
                        values[index] = updated
                    }
                    current.copy(values = values)
                }
                valueIndex = null
            },
        )
    }

    buttonIndex?.let { index ->
        ButtonEditorDialog(
            initial = def.buttons.getOrNull(index),
            entities = vm.entities,
            onDismiss = { buttonIndex = null },
            onConfirm = { updated ->
                vm.updateEditor { current ->
                    val buttons = current.buttons.toMutableList()
                    if (index == NEW_ENTRY || index >= buttons.size) {
                        buttons.add(updated)
                    } else {
                        buttons[index] = updated
                    }
                    current.copy(buttons = buttons)
                }
                buttonIndex = null
            },
        )
    }
}

/** -1 steht für „neuer Eintrag“. */
private const val NEW_ENTRY = -1

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onEdit) { Text("Ändern") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Entfernen",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
