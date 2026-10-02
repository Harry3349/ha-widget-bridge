package de.reimann.hawidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.data.HaEntity
import de.reimann.hawidget.data.WidgetButton
import de.reimann.hawidget.data.WidgetValue
import de.reimann.hawidget.icons.MdiIcons

/** Aus einem Text einen Schlüssel im Slug-Format machen (wie die Integration). */
internal fun slugify(value: String): String {
    val lowered = value.lowercase()
        .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    return lowered.replace(Regex("[^a-z0-9]+"), "_").trim('_').take(40).ifBlank { "button" }
}

@Composable
fun EntityPickerDialog(
    title: String,
    entities: List<HaEntity>,
    onDismiss: () -> Unit,
    onPick: (HaEntity) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    val filtered = remember(query, entities) {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) {
            entities.take(300)
        } else {
            entities.filter {
                it.entityId.lowercase().contains(needle) || it.name.lowercase().contains(needle)
            }.take(300)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                when {
                    entities.isEmpty() -> Text(
                        "Entities werden geladen …",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    filtered.isEmpty() -> Text(
                        "Kein Treffer",
                        style = MaterialTheme.typography.bodySmall,
                    )

                    else -> LazyColumn(modifier = Modifier.height(320.dp)) {
                        items(filtered) { entity ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(entity) }
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(entity.name, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    entity.entityId,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
fun ValueEditorDialog(
    initial: WidgetValue?,
    entities: List<HaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (WidgetValue) -> Unit,
) {
    var entity by remember { mutableStateOf(initial?.entity.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color.orEmpty()) }
    var pickerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Wert hinzufügen" else "Wert bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = entity,
                    onValueChange = { entity = it },
                    label = { Text("Entity-ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickerOpen = true }) { Text("Entity auswählen") }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Beschriftung (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Farbe (optional, z. B. #4DD0E1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Ohne Farbe: grün ab dem Schwellwert (Standard 0,5), sonst grau – " +
                        "nicht verfügbare Werte immer rot.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = entity.isNotBlank(),
                onClick = {
                    onConfirm(
                        WidgetValue(
                            entity = entity.trim(),
                            label = label.trim().ifBlank { null },
                            threshold = initial?.threshold,
                            color = color.trim().ifBlank { null },
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )

    if (pickerOpen) {
        EntityPickerDialog(
            title = "Entity auswählen",
            entities = entities,
            onDismiss = { pickerOpen = false },
            onPick = { picked ->
                entity = picked.entityId
                if (label.isBlank()) label = picked.name
                pickerOpen = false
            },
        )
    }
}

@Composable
fun ButtonEditorDialog(
    initial: WidgetButton?,
    entities: List<HaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (WidgetButton) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var service by remember { mutableStateOf(initial?.service ?: "switch.toggle") }
    var entityId by remember { mutableStateOf(initial?.entityId.orEmpty()) }
    var stateEntity by remember { mutableStateOf(initial?.stateEntity.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon ?: MdiIcons.DEFAULT) }
    var showState by remember { mutableStateOf(initial?.showState ?: true) }
    var pickerTarget by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Button hinzufügen" else "Button bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Beschriftung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = service,
                    onValueChange = { service = it },
                    label = { Text("Service (z. B. switch.toggle)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    listOf("switch.toggle", "light.toggle", "script.turn_on", "automation.trigger")
                        .forEach { preset ->
                            OutlinedButton(
                                onClick = { service = preset },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            ) { Text(preset, style = MaterialTheme.typography.bodySmall) }
                        }
                }

                OutlinedTextField(
                    value = entityId,
                    onValueChange = { entityId = it },
                    label = { Text("Entity-ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickerTarget = "entity" }) { Text("Entity auswählen") }

                OutlinedTextField(
                    value = stateEntity,
                    onValueChange = { stateEntity = it },
                    label = { Text("Zustand anzeigen (Entity, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickerTarget = "state" }) { Text("Zustands-Entity wählen" ) }

                // „An/Aus“ neben dem Namen anzeigen oder nicht
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = showState, onCheckedChange = { showState = it })
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("An/Aus anzeigen", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Zeigt den Zustand der Zustands-Entity neben der Beschriftung.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text("Symbol", style = MaterialTheme.typography.bodyMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    MdiIcons.choices.forEach { (name, drawable) ->
                        val selected = name == icon
                        IconButton(onClick = { icon = name }) {
                            Icon(
                                painter = painterResource(drawable),
                                contentDescription = name,
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && service.contains("."),
                onClick = {
                    onConfirm(
                        WidgetButton(
                            key = initial?.key ?: slugify(label),
                            label = label.trim(),
                            icon = icon,
                            service = service.trim(),
                            entityId = entityId.trim().ifBlank { null },
                            stateEntity = stateEntity.trim().ifBlank { null },
                            showState = showState,
                            stateLabelOn = initial?.stateLabelOn,
                            stateLabelOff = initial?.stateLabelOff,
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )

    if (pickerTarget.isNotEmpty()) {
        EntityPickerDialog(
            title = "Entity auswählen",
            entities = entities,
            onDismiss = { pickerTarget = "" },
            onPick = { picked ->
                if (pickerTarget == "entity") {
                    entityId = picked.entityId
                    if (label.isBlank()) label = picked.name
                } else {
                    stateEntity = picked.entityId
                }
                pickerTarget = ""
            },
        )
    }
}
