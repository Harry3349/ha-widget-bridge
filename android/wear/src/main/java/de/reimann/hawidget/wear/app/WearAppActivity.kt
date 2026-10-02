package de.reimann.hawidget.wear.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import de.reimann.hawidget.wear.R
import de.reimann.hawidget.wear.data.Bridge
import de.reimann.hawidget.wear.data.RowItem
import de.reimann.hawidget.wear.data.Settings
import de.reimann.hawidget.wear.data.WidgetJson
import de.reimann.hawidget.wear.icons.TileIcons
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Die volle Liste auf der Uhr.
 *
 * Kacheln können laut Wear OS **nicht** scrollen – deshalb öffnet ein Tipp auf
 * die Kachel diese Ansicht. Sie zeigt dieselben Zeilen wie Kachel und Handy,
 * lässt sich aber mit dem Finger **und** mit der Krone (Drehknopf) scrollen.
 *
 * Die Uhr holt nichts selbst aus Home Assistant: Angezeigt wird der Snapshot,
 * den die Handy-App über den Wearable Data Layer ablegt. Ein Tipp auf einen
 * Button schickt das Kommando an das Handy.
 */
class WearAppActivity : Activity() {

    private lateinit var scroll: ScrollView
    private lateinit var column: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private val rerender = Runnable { render() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Unten bewusst viel Platz: nur so lässt sich die letzte Zeile bis in
            // die Mitte scrollen, wo die runde Anzeige breit genug ist.
            setPadding(dp(SIDE_PADDING_DP), dp(12f), dp(SIDE_PADDING_DP), dp(BOTTOM_PADDING_DP))
        }
        scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(column)
        }
        setContentView(scroll)
        render()
    }

    override fun onResume() {
        super.onResume()
        render()
        // Das Handy überträgt danach einen frischen Stand (es hat das Netz).
        Bridge.refresh(this)
    }

    override fun onDestroy() {
        handler.removeCallbacks(rerender)
        super.onDestroy()
    }

    /**
     * Krone: Wear OS liefert Drehbewegungen als ``ACTION_SCROLL`` vom
     * Drehgeber. Ein Rastschritt bewegt die Liste um etwa eine halbe Zeile.
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER) &&
            event.action == MotionEvent.ACTION_SCROLL
        ) {
            val delta = event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(48f)
            scroll.scrollBy(0, -delta.toInt())
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    // ------------------------------------------------------------- Aufbau

    private fun render() {
        column.removeAllViews()

        val settings = Settings(this)
        val snapshot = settings.snapshotJson?.let { raw ->
            runCatching { WidgetJson.parseSnapshot(raw) }.getOrNull()
        }

        val title = snapshot?.name?.takeIf { it.isNotBlank() }
            ?: getString(R.string.tile_not_configured)
        column.addView(block(title, 15f, Color.WHITE, Typeface.BOLD, Gravity.CENTER_HORIZONTAL))

        val stale = settings.lastRefresh > 0L &&
            System.currentTimeMillis() - settings.lastRefresh > STALE_MS
        val note = when {
            System.currentTimeMillis() - settings.pressFailedAt < PRESS_FAILED_MS ->
                getString(R.string.tile_press_failed) to KEY_COLOR

            snapshot == null -> getString(R.string.tile_loading) to NOTE_COLOR

            stale -> getString(R.string.tile_stale, timeOf(settings.lastRefresh)) to KEY_COLOR

            else -> getString(R.string.tile_updated, timeOf(settings.lastRefresh)) to NOTE_COLOR
        }
        column.addView(block(note.first, 11f, note.second, Typeface.NORMAL, Gravity.CENTER_HORIZONTAL))

        val rows = snapshot?.watchRowsList.orEmpty()
        if (rows.isEmpty()) {
            column.addView(
                block(
                    getString(R.string.wear_list_empty),
                    12f,
                    NOTE_COLOR,
                    Typeface.NORMAL,
                    Gravity.CENTER_HORIZONTAL,
                )
            )
            return
        }

        // Schriftgröße aus dem Editor, angepasst an die Einstellung „Uhr“
        val scale = snapshot?.watchScale ?: 1f

        rows.forEach { row ->
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(4f) }
            }
            row.items.take(MAX_ROW_ITEMS).forEach { item ->
                line.addView(cell(item, scale, snapshot?.id))
            }
            column.addView(line)
        }

        column.addView(
            block(
                getString(R.string.wear_list_hint),
                10f,
                HINT_COLOR,
                Typeface.NORMAL,
                Gravity.CENTER_HORIZONTAL,
            )
        )
    }

    /**
     * Ein Objekt einer Zeile: Text, Sensor (Name über Wert) oder Button.
     *
     * ``widgetId`` wird beim Druck mitgeschickt, damit das Handy weiß, welche
     * Fassung den Knopf enthält (mehrere Uhren können verschiedene zeigen).
     */
    private fun cell(item: RowItem, scale: Float, widgetId: String?): View {
        val align = alignGravity(item.align)
        val size = (item.size * scale).coerceIn(8f, 30f)
        val small = (size - 3f).coerceAtLeast(9f)

        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Beim Button bestimmt das dehnbare Titelfeld die Lage: Symbol links,
            // „An/Aus“ rechts – nur der Titel folgt der Ausrichtung.
            gravity = Gravity.CENTER_VERTICAL or
                if (item.type == "button") Gravity.START else align
            // Blöcke mit eigener Breite bekommen ein passendes Gewicht,
            // die übrigen teilen sich den Rest gleichmäßig.
            val weight = if (item.width > 0f) item.width else 0f
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                if (weight > 0f) weight else 1f,
            )
        }

        when (item.type) {
            "sensor" -> {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = align
                }
                // Ohne eingetragenen Namen steht nur der Wert
                val label = item.label.orEmpty()
                if (label.isNotBlank()) {
                    box.addView(inline(label, small, LABEL_COLOR))
                }
                box.addView(inline(item.text.orEmpty(), size, parseColor(item.color, Color.WHITE)))
                cell.addView(box)
            }

            "button" -> {
                val iconSize = dp(16f)
                cell.addView(
                    ImageView(this).apply {
                        setImageResource(TileIcons.drawable(item.icon))
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            rightMargin = dp(6f)
                        }
                    }
                )
                cell.addView(
                    inline(
                        item.label.orEmpty(),
                        size,
                        if (item.active) ACCENT else Color.WHITE,
                        gravity = align,
                        weight = 1f,
                    )
                )
                val state = item.stateLabel.orEmpty()
                // „An/Aus“ nur zeigen, wenn es im Editor eingeschaltet ist
                if (item.showState && state.isNotBlank()) {
                    cell.addView(
                        inline(" $state", small, if (item.active) ACCENT else LABEL_COLOR)
                    )
                }

                cell.setPadding(dp(6f), dp(6f), dp(6f), dp(6f))
                cell.background = rounded(
                    if (!item.available) FAILED_BACKGROUND
                    else if (item.active) ACTIVE_BACKGROUND
                    else BUTTON_BACKGROUND
                )
                cell.isClickable = true
                cell.setOnClickListener {
                    val key = item.key
                    if (key.isNullOrBlank()) return@setOnClickListener
                    Bridge.press(this, widgetId, key)
                    // Das Handy schaltet und schickt den neuen Stand zurück
                    handler.removeCallbacks(rerender)
                    handler.postDelayed(rerender, PRESS_RERENDER_MS)
                }
            }

            else -> cell.addView(inline(item.text.orEmpty(), size, parseColor(item.color, Color.WHITE)))
        }

        return cell
    }

    // -------------------------------------------------------------- Helfer

    private fun block(
        text: String,
        sizeSp: Float,
        color: Int,
        style: Int,
        gravity: Int,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, style)
        this.gravity = gravity
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
    }

    /**
     * Ein Textfeld. Mit ``weight`` > 0 dehnt es sich aus (z. B. der Button-Titel,
     * der das Symbol nach links und „An/Aus“ nach rechts schiebt).
     */
    private fun inline(
        text: String,
        sizeSp: Float,
        color: Int,
        gravity: Int = Gravity.START,
        weight: Float = 0f,
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        this.gravity = gravity
        maxLines = 2
        layoutParams = LinearLayout.LayoutParams(
            if (weight > 0f) 0 else LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            weight,
        )
    }

    private fun rounded(color: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(10f).toFloat()
    }

    private fun alignGravity(align: String): Int = when (align.lowercase()) {
        "left" -> Gravity.START
        "right" -> Gravity.END
        else -> Gravity.CENTER_HORIZONTAL
    }

    private fun parseColor(value: String?, fallback: Int): Int {
        val raw = value?.trim().orEmpty()
        if (!raw.startsWith("#")) return fallback
        val hex = raw.removePrefix("#")
        return runCatching {
            when (hex.length) {
                6 -> (0xFF000000L or hex.toLong(16)).toInt()
                8 -> hex.toLong(16).toInt()
                else -> fallback
            }
        }.getOrDefault(fallback)
    }

    private fun timeOf(millis: Long): String {
        if (millis <= 0L) return "–"
        return runCatching {
            Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("–")
    }

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    private companion object {
        const val MAX_ROW_ITEMS = 3
        const val STALE_MS = 10 * 60 * 1000L
        const val PRESS_FAILED_MS = 20 * 1000L
        const val PRESS_RERENDER_MS = 1500L

        /** Seitlicher Abstand: hält den Text aus der Rundung heraus. */
        const val SIDE_PADDING_DP = 20f

        /** Zusätzlicher Platz unter der letzten Zeile (Scrollbereich). */
        const val BOTTOM_PADDING_DP = 80f

        val ACCENT = 0xFF00E676.toInt()
        val NOTE_COLOR = 0xFF888888.toInt()
        val LABEL_COLOR = 0xFF999999.toInt()
        val HINT_COLOR = 0xFF777777.toInt()
        val KEY_COLOR = ACCENT
        const val BUTTON_BACKGROUND = 0x26FFFFFF
        const val ACTIVE_BACKGROUND = 0x6600E676
        const val FAILED_BACKGROUND = 0x66FF5252
    }
}
