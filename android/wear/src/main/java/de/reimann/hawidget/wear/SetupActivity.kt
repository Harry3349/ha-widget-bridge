package de.reimann.hawidget.wear

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import de.reimann.hawidget.wear.data.HaClient
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson
import de.reimann.hawidget.wear.work.Workers

/**
 * Minimale Einrichtung auf der Uhr: Adresse und Token eintragen, danach wird
 * das erste in Home Assistant angelegte Widget übernommen.
 *
 * Die Oberfläche ist absichtlich mit einfachen Views gebaut (kein Compose),
 * damit das Wear-Modul klein und robust bleibt.
 */
class SetupActivity : Activity() {

    private lateinit var urlField: EditText
    private lateinit var tokenField: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(24))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        column.addView(heading(getString(R.string.setup_title)))
        column.addView(hint(getString(R.string.setup_hint)))

        urlField = EditText(this).apply {
            setText(settings.baseUrl)
            hint = getString(R.string.setup_url)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        column.addView(urlField)

        tokenField = EditText(this).apply {
            setText(settings.token)
            hint = getString(R.string.setup_token)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            isSingleLine = true
        }
        column.addView(tokenField)

        val saveButton = Button(this).apply {
            text = getString(R.string.setup_save)
            setOnClickListener { save(settings) }
        }
        column.addView(saveButton)

        statusView = TextView(this).apply {
            text = if (settings.isConfigured) {
                getString(R.string.setup_status, settings.widgetId.ifBlank { "?" })
            } else {
                getString(R.string.setup_open)
            }
            textSize = 11f
            setPadding(0, dp(12), 0, 0)
        }
        column.addView(statusView)

        val scroll = ScrollView(this).apply { addView(column) }
        setContentView(scroll)
    }

    private fun save(settings: Settings) {
        settings.baseUrl = urlField.text.toString()
        settings.token = tokenField.text.toString()
        statusView.text = getString(R.string.setup_testing)

        Thread {
            val message = try {
                if (!settings.isConfigured) {
                    getString(R.string.setup_need_both)
                } else {
                    val client = HaClient(settings.baseUrl, settings.token)
                    val widgetId = WidgetJson.firstWidgetId(client.listWidgets()).orEmpty()
                    if (widgetId.isBlank()) {
                        getString(R.string.setup_no_widget)
                    } else {
                        settings.widgetId = widgetId
                        settings.snapshotJson = client.snapshotRaw(widgetId)
                        settings.lastRefresh = System.currentTimeMillis()
                        Workers.schedulePeriodic(this)
                        Workers.updateTile(this)
                        getString(R.string.setup_ok, widgetId)
                    }
                }
            } catch (error: Exception) {
                error.message ?: getString(R.string.setup_failed)
            }
            runOnUiThread { statusView.text = message }
        }.start()
    }

    // ------------------------------------------------------------------ Views

    private fun heading(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 16f
        setPadding(0, 0, 0, dp(4))
    }

    private fun hint(value: String): TextView = TextView(this).apply {
        text = value
        textSize = 11f
        gravity = Gravity.START
        setPadding(0, 0, 0, dp(10))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
