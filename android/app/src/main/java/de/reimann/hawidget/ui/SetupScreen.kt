package de.reimann.hawidget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun SetupScreen(vm: MainViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Verbindung", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = vm.url,
            onValueChange = { vm.url = it },
            label = { Text("Server-URL") },
            placeholder = { Text("https://homeassistant.local:8123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = vm.token,
            onValueChange = { vm.token = it },
            label = { Text("Long-Lived Access Token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveSettings() }) { Text("Speichern") }
            OutlinedButton(onClick = { vm.testConnection() }) { Text("Testen") }
        }

        if (vm.connectionStatus.isNotBlank()) {
            Text(vm.connectionStatus, style = MaterialTheme.typography.bodyMedium)
        }

        HorizontalDivider()

        Text("Aktualisierung", style = MaterialTheme.typography.titleMedium)

        OutlinedTextField(
            value = vm.refreshMinutes,
            onValueChange = { vm.refreshMinutes = it.filter(Char::isDigit) },
            label = { Text("Alle X Minuten abrufen (15–240)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            "Abgerufen wird nur, wenn der Bildschirm an ist – im Standby entfallen die " +
                "Abrufe. Antippen des Widgets holt jederzeit den aktuellen Stand.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = vm.liveMode,
                onCheckedChange = {
                    vm.liveMode = it
                    vm.saveSettings()
                },
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Live-Modus")
                Text(
                    "Echtzeit-Aktualisierung per WebSocket-Dienst. Verbraucht mehr Akku " +
                        "und zeigt eine dauerhafte Benachrichtigung. Bei ausgeschaltetem " +
                        "Bildschirm pausiert der Dienst und verbindet sich beim Einschalten " +
                        "neu (aktualisiert dann sofort).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()

        Text(
            "Der Token wird nur auf diesem Gerät gespeichert (keine Cloud-Backups). " +
                "In Home Assistant: Profil → Sicherheit → Long-Lived Access Tokens.\n\n" +
                "Voraussetzung: Die Integration „HA Widget Bridge“ ist in Home Assistant " +
                "eingerichtet (HACS → Integrationen).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
