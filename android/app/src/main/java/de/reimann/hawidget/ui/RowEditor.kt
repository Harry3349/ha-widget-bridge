package de.reimann.hawidget.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import de.reimann.hawidget.data.HaEntity
import de.reimann.hawidget.data.RowDef
import de.reimann.hawidget.data.RowItem
import de.reimann.hawidget.icons.MdiIcons

/** Wie viele Zeilen die Widgets überhaupt darstellen können. */
internal const val MAX_ROW_LINES = 8
internal const val MAX_ROW_OBJECTS = 3

/**
 * Editor für das Zeilen-Layout.
 *
 * Die Zeilen gelten gleichzeitig für das Handy-Widget und die Uhr-Kachel, weil
 * beide dieselbe Definition aus Home Assistant zeichnen. Deshalb stehen unter
 * der Liste auch die Hinweise für beide Flächen (ab wann das Handy-Widget höher
 * gezogen werden muss bzw. ab wann die Uhr scrollt).
 */
@Composable
fun RowEditorSection(
    rows: List<RowDef>,
    entities: List<HaEntity>,
    onRowsChange: (List<RowDef>) -> Unit,
) {
    var dialog by remember { mutableStateOf<RowDialog?>(null) }

    Text("Zeilen (Handy + Uhr)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Gilt für das Widget auf dem Handy und die Kachel auf der Uhr gleichzeitig. " +
            "Pro Zeile bis zu drei Objekte – Text, Sensor oder Button – jeweils mit " +
            "eigenem Text, eigener Ausrichtung (links/mitte/rechts) und Schriftgröße. " +
            "Sobald eine Zeile angelegt ist, ersetzt dieses Layout die Werte- und " +
            "Button-Liste oben. Auf der Uhr passen nur so viele Zeilen auf die Kachel, " +
            "wie ohne Scrollen hineingehen; ein Tipp auf die Kachel öffnet die volle " +
            "Liste, die sich mit Wischen oder Krone scrollen lässt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val itemsPerRow = rows.map { it.items }
    val phoneGrow = RowCapacity.phoneGrowFrom(itemsPerRow)
    val watchScroll = RowCapacity.watchScrollFrom(itemsPerRow)

    if (rows.isEmpty()) {
        Text(
            "Noch keine Zeile angelegt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    rows.forEachIndexed { rowIndex, row ->
        if (phoneGrow != null && rowIndex == phoneGrow - 1) {
            CapacityDivider("Ab hier braucht das Handy-Widget mehr Höhe")
        }
        if (watchScroll != null && rowIndex == watchScroll - 1) {
            CapacityDivider("Ab hier auf der Uhr nur in der App (Kachel antippen)")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zeile ${rowIndex + 1}", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { onRowsChange(rows.moveItem(rowIndex, -1)) },
                        enabled = rowIndex > 0,
                    ) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Nach oben")
                    }
                    IconButton(
                        onClick = { onRowsChange(rows.moveItem(rowIndex, 1)) },
                        enabled = rowIndex < rows.size - 1,
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Nach unten")
                    }
                    IconButton(onClick = { onRowsChange(rows.withoutRow(rowIndex)) }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Zeile entfernen",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                row.items.forEachIndexed { itemIndex, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 4.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.summary, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Ausrichtung ${alignLabel(item.align)} · ${item.size.toInt()} sp",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { dialog = RowDialog.Edit(rowIndex, itemIndex) }) {
                            Text("Ändern")
                        }
                        IconButton(
                            onClick = { onRowsChange(rows.withoutItem(rowIndex, itemIndex)) },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Objekt entfernen",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                if (row.items.size < MAX_ROW_OBJECTS) {
                    OutlinedButton(
                        onClick = { dialog = RowDialog.PickType(rowIndex) },
                        modifier = Modifier.padding(top = 4.dp),
                    ) { Text("Objekt hinzufügen") }
                }
            }
        }
    }

    OutlinedButton(
        onClick = { onRowsChange(rows + RowDef()) },
        enabled = rows.size < MAX_ROW_LINES,
    ) { Text("Zeile hinzufügen") }

    if (rows.size >= MAX_ROW_LINES) {
        Text(
            "Mehr als $MAX_ROW_LINES Zeilen kann das Widget nicht darstellen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // ------------------------------------------------------------ Hinweise
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Wie viel Platz ist da?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(RowCapacity.phoneHint(itemsPerRow), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(RowCapacity.watchHint(itemsPerRow), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "Schätzung: ca. ${RowCapacity.heightDp(itemsPerRow).toInt()} dp Inhaltshöhe. " +
                    "Genaue Werte hängen von der Kachelgröße im Launcher (Handy) und der " +
                    "Anzeigegröße (Uhr) ab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // ------------------------------------------------------------- Dialoge
    val current = dialog
    if (current != null) {
        when (current) {
            is RowDialog.PickType -> RowTypeDialog(
                onDismiss = { dialog = null },
                onPick = { type -> dialog = RowDialog.Edit(current.row, NEW_ITEM, type) },
            )

            is RowDialog.Edit -> {
                val initial = rows.getOrNull(current.row)?.items?.getOrNull(current.index)
                val type = initial?.type ?: current.type
                val confirm: (RowItem) -> Unit = { item ->
                    onRowsChange(rows.withItem(current.row, current.index, item))
                    dialog = null
                }

                when (type) {
                    "sensor" -> RowSensorDialog(
                        initial = initial,
                        entities = entities,
                        onDismiss = { dialog = null },
                        onConfirm = confirm,
                    )

                    "button" -> RowButtonDialog(
                        initial = initial,
                        entities = entities,
                        onDismiss = { dialog = null },
                        onConfirm = confirm,
                    )

                    else -> RowTextDialog(
                        initial = initial,
                        onDismiss = { dialog = null },
                        onConfirm = confirm,
                    )
                }
            }
        }
    }
}

private const val NEW_ITEM = -1

private sealed interface RowDialog {
    data class PickType(val row: Int) : RowDialog
    data class Edit(val row: Int, val index: Int, val type: String = "text") : RowDialog
}

// ------------------------------------------------------------ Listen-Helfer

private fun List<RowDef>.withoutRow(index: Int): List<RowDef> =
    filterIndexed { i, _ -> i != index }

private fun List<RowDef>.moveItem(index: Int, delta: Int): List<RowDef> {
    val target = index + delta
    if (target < 0 || target >= size) return this
    val result = toMutableList()
    val row = result.removeAt(index)
    result.add(target, row)
    return result
}

private fun List<RowDef>.withoutItem(rowIndex: Int, itemIndex: Int): List<RowDef> =
    mapIndexed { i, row ->
        if (i != rowIndex) row else RowDef(items = row.items.filterIndexed { j, _ -> j != itemIndex })
    }

private fun List<RowDef>.withItem(rowIndex: Int, itemIndex: Int, item: RowItem): List<RowDef> =
    mapIndexed { i, row ->
        if (i != rowIndex) {
            row
        } else {
            val items = row.items.toMutableList()
            if (itemIndex == NEW_ITEM || itemIndex >= items.size) {
                items.add(item)
            } else {
                items[itemIndex] = item
            }
            RowDef(items = items)
        }
    }

internal fun alignLabel(align: String): String = when (align.lowercase()) {
    "left" -> "links"
    "right" -> "rechts"
    else -> "mittig"
}

// ------------------------------------------------------------------ Dialoge

@Composable
private fun CapacityDivider(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun RowTypeDialog(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Objekt hinzufügen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = { onPick("text") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Text") }
                OutlinedButton(
                    onClick = { onPick("sensor") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sensor (Entity-Wert)") }
                OutlinedButton(
                    onClick = { onPick("button") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Button (schaltet in Home Assistant)") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/** Ausrichtung und Schriftgröße – in allen Objekt-Dialogen gleich. */
@Composable
private fun AlignSizeFields(
    align: String,
    onAlign: (String) -> Unit,
    size: String,
    onSize: (String) -> Unit,
) {
    Text("Ausrichtung", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("left", "center", "right").forEach { value ->
            FilterChip(
                selected = align == value,
                onClick = { onAlign(value) },
                label = { Text(alignLabel(value)) },
            )
        }
    }

    OutlinedTextField(
        value = size,
        onValueChange = { text -> onSize(text.filter(Char::isDigit).take(2)) },
        label = { Text("Schriftgröße (8–30)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun sizeOf(value: String, fallback: Float): Float =
    (value.toFloatOrNull() ?: fallback).coerceIn(8f, 30f)

@Composable
private fun RowTextDialog(
    initial: RowItem?,
    onDismiss: () -> Unit,
    onConfirm: (RowItem) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var align by remember { mutableStateOf(initial?.align ?: "center") }
    var size by remember { mutableStateOf((initial?.size ?: 14f).toInt().toString()) }
    var color by remember { mutableStateOf(initial?.color.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Text hinzufügen" else "Text bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = color,
                    onValueChange = { color = it },
                    label = { Text("Farbe (optional, z. B. #4DD0E1)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AlignSizeFields(align, { align = it }, size, { size = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    onConfirm(
                        RowItem(
                            type = "text",
                            text = text.trim(),
                            color = color.trim().ifBlank { null },
                            align = align,
                            size = sizeOf(size, 14f),
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

@Composable
private fun RowSensorDialog(
    initial: RowItem?,
    entities: List<HaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (RowItem) -> Unit,
) {
    var entity by remember { mutableStateOf(initial?.entity.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color.orEmpty()) }
    var align by remember { mutableStateOf(initial?.align ?: "center") }
    var size by remember { mutableStateOf((initial?.size ?: 14f).toInt().toString()) }
    var pickerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Sensor hinzufügen" else "Sensor bearbeiten") },
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
                AlignSizeFields(align, { align = it }, size, { size = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = entity.isNotBlank(),
                onClick = {
                    onConfirm(
                        RowItem(
                            type = "sensor",
                            entity = entity.trim(),
                            label = label.trim().ifBlank { null },
                            color = color.trim().ifBlank { null },
                            align = align,
                            size = sizeOf(size, 14f),
                            threshold = initial?.threshold,
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
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
private fun RowButtonDialog(
    initial: RowItem?,
    entities: List<HaEntity>,
    onDismiss: () -> Unit,
    onConfirm: (RowItem) -> Unit,
) {
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var service by remember { mutableStateOf(initial?.service ?: "switch.toggle") }
    var entityId by remember { mutableStateOf(initial?.entityId.orEmpty()) }
    var stateEntity by remember { mutableStateOf(initial?.stateEntity.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon ?: MdiIcons.DEFAULT) }
    var align by remember { mutableStateOf(initial?.align ?: "center") }
    var size by remember { mutableStateOf((initial?.size ?: 14f).toInt().toString()) }
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
                OutlinedButton(onClick = { pickerTarget = "state" }) { Text("Zustands-Entity wählen") }

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

                AlignSizeFields(align, { align = it }, size, { size = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && service.contains("."),
                onClick = {
                    onConfirm(
                        RowItem(
                            type = "button",
                            key = initial?.key ?: slugify(label),
                            label = label.trim(),
                            icon = icon,
                            service = service.trim(),
                            entityId = entityId.trim().ifBlank { null },
                            stateEntity = stateEntity.trim().ifBlank { null },
                            stateLabelOn = initial?.stateLabelOn,
                            stateLabelOff = initial?.stateLabelOff,
                            align = align,
                            size = sizeOf(size, 14f),
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
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
