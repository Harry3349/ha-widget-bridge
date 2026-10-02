package de.reimann.hawidget.wear.icons

import androidx.annotation.DrawableRes
import de.reimann.hawidget.wear.R

/**
 * Derselbe Symbolsatz wie in der Handy-App: ``mdi:``-Namen werden über
 * Schlüsselwörter auf die mitgelieferten Symbole abgebildet.
 *
 * Für die Tile muss jedes Symbol über ``id`` in den Tile-Ressourcen liegen
 * (siehe [all] und ``WidgetTileService.onTileResourcesRequest``).
 */
object TileIcons {

    @DrawableRes
    fun drawable(icon: String?): Int = when (name(icon)) {
        ID_PRINTER -> R.drawable.ic_printer
        ID_DESKTOP -> R.drawable.ic_desktop
        ID_LIGHT -> R.drawable.ic_light
        ID_PLUG -> R.drawable.ic_plug
        ID_TEMP -> R.drawable.ic_temp
        ID_TOGGLE -> R.drawable.ic_toggle
        ID_POWER -> R.drawable.ic_power
        else -> R.drawable.ic_switch
    }

    /** Kennung des Symbols in den Tile-Ressourcen (muss zu [all] passen). */
    fun id(icon: String?): String = name(icon)

    /** Alle Symbole, die der Tile-Dienst bereitstellen muss. */
    val all: List<Pair<String, Int>>
        get() = listOf(
            ID_PRINTER to R.drawable.ic_printer,
            ID_DESKTOP to R.drawable.ic_desktop,
            ID_LIGHT to R.drawable.ic_light,
            ID_PLUG to R.drawable.ic_plug,
            ID_TEMP to R.drawable.ic_temp,
            ID_TOGGLE to R.drawable.ic_toggle,
            ID_POWER to R.drawable.ic_power,
            ID_SWITCH to R.drawable.ic_switch,
        )

    private const val ID_PRINTER = "icon_printer"
    private const val ID_DESKTOP = "icon_desktop"
    private const val ID_LIGHT = "icon_light"
    private const val ID_PLUG = "icon_plug"
    private const val ID_TEMP = "icon_temp"
    private const val ID_TOGGLE = "icon_toggle"
    private const val ID_POWER = "icon_power"
    private const val ID_SWITCH = "icon_switch"

    private fun name(icon: String?): String {
        val raw = icon?.removePrefix("mdi:")?.lowercase().orEmpty()
        return when {
            raw.contains("printer") || raw.contains("print") -> ID_PRINTER
            raw.contains("desktop") || raw.contains("monitor") ||
                raw.contains("laptop") || raw.contains("computer") -> ID_DESKTOP
            raw.contains("light") || raw.contains("bulb") || raw.contains("lamp") -> ID_LIGHT
            raw.contains("plug") || raw.contains("socket") || raw.contains("outlet") -> ID_PLUG
            raw.contains("therm") || raw.contains("temperature") -> ID_TEMP
            raw.contains("toggle") -> ID_TOGGLE
            raw.contains("power") || raw.contains("flash") || raw.contains("lightning") -> ID_POWER
            else -> ID_SWITCH
        }
    }
}
