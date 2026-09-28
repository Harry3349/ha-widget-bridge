package de.reimann.hawidget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import de.reimann.hawidget.data.Settings
import de.reimann.hawidget.ui.HaWidgetBridgeTheme
import de.reimann.hawidget.ui.MainViewModel
import de.reimann.hawidget.ui.Screen
import de.reimann.hawidget.ui.SetupScreen
import de.reimann.hawidget.ui.WidgetEditorScreen
import de.reimann.hawidget.ui.WidgetListScreen

/** Einstiegspunkt: Einrichtung, Widget-Verwaltung und Editor. */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Für den Live-Modus (Vordergrunddienst) braucht es ab Android 13 die
        // Benachrichtigungs-Berechtigung.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }

        val configured = Settings(applicationContext).isConfigured

        setContent {
            HaWidgetBridgeTheme {
                val vm: MainViewModel = viewModel()
                var screen by remember {
                    mutableStateOf(if (configured) Screen.WIDGETS else Screen.SETUP)
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // targetSdk 35 zeichnet randlos – ohne Innenabstände läge die
                    // Statusleiste über der Meldungszeile.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .safeDrawingPadding(),
                    ) {

                        vm.message?.let { text ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = 16.dp,
                                        end = 8.dp,
                                        top = 4.dp,
                                        bottom = 4.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { vm.message = null }) { Text("OK") }
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (screen) {
                                Screen.SETUP -> SetupScreen(vm)

                                Screen.WIDGETS -> WidgetListScreen(
                                    vm = vm,
                                    onEdit = { screen = Screen.EDITOR },
                                    onAddToHomeScreen = { vm.pinWidget() },
                                )

                                Screen.EDITOR -> WidgetEditorScreen(
                                    vm = vm,
                                    onBack = { screen = Screen.WIDGETS },
                                )
                            }
                        }

                        NavigationBar {
                            NavigationBarItem(
                                selected = screen != Screen.SETUP,
                                onClick = { screen = Screen.WIDGETS },
                                icon = { Icon(Icons.Default.Home, contentDescription = null) },
                                label = { Text("Widgets") },
                            )
                            NavigationBarItem(
                                selected = screen == Screen.SETUP,
                                onClick = { screen = Screen.SETUP },
                                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                label = { Text("Einrichtung") },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1001
    }
}
