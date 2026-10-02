package de.reimann.hawidget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.reimann.hawidget.data.RowDef
import de.reimann.hawidget.data.RowItem
import de.reimann.hawidget.data.WidgetButton
import de.reimann.hawidget.data.WidgetValue

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
    values: List<WidgetValue>,
    buttons: List<WidgetButton>,
    watchRows: Int,
    watchScale: Float,
    onRowsChange: (List<RowDef>) -> Unit,
    onWatchChange: (Int, Float) -> Unit,
) {
    var dialog by remember { mutableStateOf<RowDialog?>(null) }

    Text("Zeilen (Handy + Uhr)", style = MaterialTheme.typography.titleMedium)
    Text(
        "Gilt für das Widget auf dem Handy und die Kachel auf der Uhr gleichzeitig. " +
            "Pro Zeile bis zu drei Objekte – Text, Sensor oder Button. Sensoren und " +
            "Buttons kommen aus den Listen oben („Werte“ und „Buttons“), dort bekommen " +
            "sie ihren Namen; hier stellst du nur Ausrichtung und Schriftgröße ein. " +
            "Sobald eine Zeile angelegt ist, ersetzt dieses Layout die Werte- und " +
            "Button-Liste oben. Auf der Uhr passen nur so viele Zeilen auf die Kachel, " +
            "wie ohne Scrollen hineingehen; ein Tipp auf die Kachel öffnet die volle " +
            "Liste, die sich mit Wischen oder Krone scrollen lässt.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val itemsPerRow = rows.map { it.items }
    val phoneGrow = RowCapacity.phoneGrowFrom(itemsPerRow)
    // Erste Zeile, die auf der Uhr nicht mehr auftaucht (berücksichtigt die Uhr-Auswahl)
    val watchCut = RowCapacity.watchCutIndex(rows, watchRows)

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
        if (watchCut != null && rowIndex == watchCut) {
            CapacityDivider("Ab hier auf der Uhr nur in der App (Kachel antippen)")
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zeile ${rowIndex + 1}", style = MaterialTheme.typography.titleSmall)
                    if (!row.watch) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "nur am Handy",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                            val missing = isMissing(item, values, buttons)
                            Text(
                                "Ausrichtung ${alignLabel(item.align)} · ${item.size.toInt()} sp · " +
                                    item.widthLabel +
                                    if (missing) " · nicht mehr in der Liste oben" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (missing) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
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

                // Uhr: einzelne Zeilen abwählen
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = row.watch,
                        onCheckedChange = { checked ->
                            onRowsChange(
                                rows.mapIndexed { index, current ->
                                    if (index == rowIndex) current.copy(watch = checked) else current
                                }
                            )
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Auf der Uhr anzeigen", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Aus: die Zeile erscheint nur im Widget am Handy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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

    // ------------------------------------------------------- Einstellungen Uhr
    Text("Uhr", style = MaterialTheme.typography.titleMedium)
    Text(
        "Die Kachel auf der Uhr ist klein. Hier stellst du ein, was sie zeigt: die " +
            "Häkchen „Auf der Uhr anzeigen“ oben je Zeile, die Zahl der Zeilen auf der " +
            "Kachel und die Schriftgröße. Alles Weitere zeigt die App auf der Uhr – die " +
            "Kachel öffnet sie per Tipp.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = watchRows.toString(),
            onValueChange = { text ->
                val parsed = text.filter(Char::isDigit).take(1).toIntOrNull() ?: 0
                onWatchChange(parsed.coerceIn(0, MAX_ROW_LINES), watchScale)
            },
            label = { Text("Zeilen auf der Kachel") },
            supportingText = { Text("0 = automatisch (so viele, wie hineinpassen)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = (watchScale * 100f).toInt().toString(),
            onValueChange = { text ->
                val parsed = text.filter(Char::isDigit).take(3).toIntOrNull() ?: 100
                onWatchChange(watchRows, (parsed.coerceIn(60, 180) / 100f))
            },
            label = { Text("Schriftgröße") },
            suffix = { Text("%") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }

    // ------------------------------------------------------------ Hinweise
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Wie viel Platz ist da?", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(RowCapacity.phoneHint(itemsPerRow), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text(
                RowCapacity.watchHint(rows, watchRows),
                style = MaterialTheme.typography.bodySmall,
            )
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
                        values = values,
                        onDismiss = { dialog = null },
                        onConfirm = confirm,
                    )

                    "button" -> RowButtonDialog(
                        initial = initial,
                        buttons = buttons,
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

/**
 * Verweist das Objekt noch auf einen Eintrag der Listen oben?
 *
 * Text-Objekte sind frei; Sensoren und Buttons müssen aus „Werte“ bzw. „Buttons“
 * stammen, sonst hat der Nutzer den Eintrag oben gelöscht.
 */
private fun isMissing(
    item: RowItem,
    values: List<WidgetValue>,
    buttons: List<WidgetButton>,
): Boolean = when (item.type) {
    "sensor" -> values.none { it.entity == item.entity }
    "button" -> buttons.none { it.key == item.key }
    else -> false
}

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

/** Ausrichtung, Schriftgröße und Blockbreite – in allen Objekt-Dialogen gleich. */
@Composable
private fun AlignSizeFields(
    align: String,
    onAlign: (String) -> Unit,
    size: String,
    onSize: (String) -> Unit,
    width: String = "",
    onWidth: (String) -> Unit = {},
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

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = size,
            onValueChange = { text -> onSize(text.filter(Char::isDigit).take(2)) },
            label = { Text("Schriftgröße (8–30)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = width,
            onValueChange = { text -> onWidth(text.filter(Char::isDigit).take(3)) },
            label = { Text("Breite") },
            suffix = { Text("%") },
            supportingText = { Text("0 = gleiche Breite") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
    }
}

private fun widthOf(value: String): Float =
    (value.toFloatOrNull() ?: 0f).coerceIn(0f, 100f)

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
    var width by remember { mutableStateOf(widthText(initial?.width)) }
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
                AlignSizeFields(align, { align = it }, size, { size = it }, width, { width = it })
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
                            width = widthOf(width),
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
    values: List<WidgetValue>,
    onDismiss: () -> Unit,
    onConfirm: (RowItem) -> Unit,
) {
    var entity by remember { mutableStateOf(initial?.entity.orEmpty()) }
    var label by remember { mutableStateOf(initial?.label.orEmpty()) }
    var color by remember { mutableStateOf(initial?.color.orEmpty()) }
    var align by remember { mutableStateOf(initial?.align ?: "center") }
    var size by remember { mutableStateOf((initial?.size ?: 14f).toInt().toString()) }
    var width by remember { mutableStateOf(widthText(initial?.width)) }

    val chosen = values.firstOrNull { it.entity == entity }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Sensor hinzufügen" else "Sensor bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (values.isEmpty()) {
                    Text(
                        "Es ist noch kein Sensor angelegt. Füge ihn zuerst oben im " +
                            "Abschnitt „Werte“ hinzu – dort bekommt er auch seinen Namen.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("Sensor aus der Liste oben", style = MaterialTheme.typography.bodyMedium)
                    PickerDropdown(
                        hint = "Sensor auswählen",
                        selected = values.firstOrNull { it.entity == entity }
                            ?.let { it.label ?: it.entity }
                            .orEmpty(),
                        options = values.map { (it.label ?: it.entity) to it.entity },
                        onSelect = { index ->
                            val value = values[index]
                            entity = value.entity
                            label = value.label.orEmpty()
                            color = value.color.orEmpty()
                        },
                    )

                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("Name in diesem Widget") },
                        supportingText = { Text("Leer lassen = es wird nur der Wert angezeigt") },
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
                    AlignSizeFields(align, { align = it }, size, { size = it }, width, { width = it })
                }
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
                            width = widthOf(width),
                            threshold = chosen?.threshold ?: initial?.threshold,
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

/**
 * Aufklappbare Auswahl: zeigt nur den gewählten Eintrag, die Liste öffnet sich
 * erst beim Antippen (statt einer dauerhaft sichtbaren Liste).
 *
 * @param options Paare aus Titel und Untertitel (z. B. Name und Entity-ID)
 */
@Composable
private fun PickerDropdown(
    hint: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = options.isNotEmpty(),
        ) {
            Text(
                selected.ifBlank { hint },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Auswahl öffnen")
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.first, style = MaterialTheme.typography.bodyLarge)
                            if (option.second.isNotBlank()) {
                                Text(
                                    option.second,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(index)
                        open = false
                    },
                )
            }
        }
    }
}

@Composable
private fun RowButtonDialog(
    initial: RowItem?,
    buttons: List<WidgetButton>,
    onDismiss: () -> Unit,
    onConfirm: (RowItem) -> Unit,
) {
    var key by remember { mutableStateOf(initial?.key.orEmpty()) }
    var align by remember { mutableStateOf(initial?.align ?: "center") }
    var size by remember { mutableStateOf((initial?.size ?: 14f).toInt().toString()) }
    var width by remember { mutableStateOf(widthText(initial?.width)) }

    val chosen = buttons.firstOrNull { it.key == key }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Button hinzufügen" else "Button bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (buttons.isEmpty()) {
                    Text(
                        "Es ist noch kein Button angelegt. Füge ihn zuerst oben im " +
                            "Abschnitt „Buttons“ hinzu – dort bekommt er Beschriftung, " +
                            "Service und Symbol.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text("Button aus der Liste oben", style = MaterialTheme.typography.bodyMedium)
                    PickerDropdown(
                        hint = "Button auswählen",
                        selected = buttons.firstOrNull { it.key == key }?.label.orEmpty(),
                        options = buttons.map { button ->
                            button.label to (button.service + (button.entityId?.let { " · $it" } ?: ""))
                        },
                        onSelect = { index -> key = buttons[index].key },
                    )
                    AlignSizeFields(align, { align = it }, size, { size = it }, width, { width = it })
                    chosen?.let { button ->
                        Text(
                            if (button.showState) {
                                "„An/Aus“ wird angezeigt – abschaltbar oben im Abschnitt „Buttons“."
                            } else {
                                "„An/Aus“ ist oben im Abschnitt „Buttons“ abgeschaltet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null,
                onClick = {
                    val button = chosen ?: return@TextButton
                    onConfirm(
                        RowItem(
                            type = "button",
                            key = button.key,
                            label = button.label,
                            icon = button.icon,
                            service = button.service,
                            entityId = button.entityId,
                            stateEntity = button.stateEntity,
                            showState = button.showState,
                            stateLabelOn = button.stateLabelOn,
                            stateLabelOff = button.stateLabelOff,
                            align = align,
                            size = sizeOf(size, 14f),
                            width = widthOf(width),
                        )
                    )
                },
            ) { Text("Übernehmen") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}


