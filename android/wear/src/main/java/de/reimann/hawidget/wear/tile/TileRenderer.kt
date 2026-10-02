package de.reimann.hawidget.wear.tile

import android.content.Context
import android.graphics.Color
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.FontStyleBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import de.reimann.hawidget.wear.R
import de.reimann.hawidget.wear.data.ButtonState
import de.reimann.hawidget.wear.data.WidgetSnapshot
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/** Baut aus einem Snapshot die Tile-Oberfläche (ProtoLayout). */
object TileRenderer {

    /** Kennung der klickbaren Buttons in der Tile. */
    const val PRESS_PREFIX = "press:"

    private const val LABEL_COLOR = 0xFF999999.toInt()
    private const val NOTE_COLOR = 0xFF888888.toInt()
    private const val BUTTON_BACKGROUND = 0x26FFFFFF
    private const val BUTTON_ACTIVE_BACKGROUND = 0x6600E676
    private const val ACCENT = 0xFF00E676.toInt()

    fun render(
        context: Context,
        snapshot: WidgetSnapshot?,
        pressed: Boolean = false,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())

        val name = snapshot?.name?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.tile_not_configured)
        column.addContent(text(name, Color.WHITE, 15f))

        val note = when {
            snapshot == null -> context.getString(R.string.tile_loading)
            !snapshot.error.isNullOrBlank() -> snapshot.error
            pressed -> context.getString(R.string.tile_pressing)
            else -> null
        }
        column.addContent(
            text(
                note ?: context.getString(R.string.tile_updated, shortTime(snapshot?.updatedAt)),
                if (note == null) NOTE_COLOR else ACCENT,
                12f,
            )
        )

        snapshot?.values?.forEach { value ->
            val row = LayoutElementBuilders.Row.Builder()
                .addContent(text(value.label, LABEL_COLOR, 13f))
                .addContent(text(" ", LABEL_COLOR, 13f))
                .addContent(text(value.text, parseColor(value.color, Color.WHITE), 13f))
            column.addContent(row.build())
        }

        val buttons = snapshot?.buttons.orEmpty()
        if (buttons.isNotEmpty()) {
            val row = LayoutElementBuilders.Row.Builder()
            buttons.take(3).forEach { button -> row.addContent(button(button)) }
            column.addContent(row.build())
        }

        return column.build()
    }

    // ---------------------------------------------------------------- Bausteine

    private fun text(value: String, color: Int, sizeSp: Float): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setColor(ColorBuilders.color(color))
            .setFontStyle(
                FontStyleBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .build()
            )
            .build()

    private fun button(state: ButtonState): LayoutElementBuilders.Box {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(PRESS_PREFIX + state.key)
            .setOnClick(ActionBuilders.LoadAction.Builder().build())
            .build()

        val background = ModifiersBuilders.Background.Builder()
            .setColor(ColorBuilders.color(if (state.active) BUTTON_ACTIVE_BACKGROUND else BUTTON_BACKGROUND))
            .setCornerRadius(DimensionBuilders.dp(15f))
            .build()

        val modifiers = ModifiersBuilders.Modifiers.Builder()
            .setClickable(clickable)
            .setBackground(background)
            .build()

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.dp(58f))
            .setHeight(DimensionBuilders.dp(30f))
            .setModifiers(modifiers)
            .setContent(text(state.label, if (state.active) ACCENT else Color.WHITE, 12f))
            .build()
    }

    // ------------------------------------------------------------------ Helfer

    /** ``#AARRGGBB`` oder ``#RRGGBB`` lesen. */
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

    /** ISO-Zeitstempel als ``HH:mm`` (lokale Uhrzeit), sonst ein Strich. */
    private fun shortTime(value: String?): String {
        if (value.isNullOrBlank()) return "–"
        return runCatching {
            OffsetDateTime.parse(value)
                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
        }.getOrDefault("–")
    }
}
