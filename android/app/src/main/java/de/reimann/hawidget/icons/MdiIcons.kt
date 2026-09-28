package de.reimann.hawidget.icons

import androidx.annotation.DrawableRes
import de.reimann.hawidget.R

/**
 * Die App bringt einen kleinen, eigenen Symbolsatz mit. ``mdi:``-Namen aus
 * Home Assistant werden über Schlüsselwörter darauf abgebildet.
 */
object MdiIcons {

    const val DEFAULT = "mdi:power"

    @DrawableRes
    fun drawable(icon: String?): Int = drawableFor(name(icon))

    @DrawableRes
    fun drawableFor(name: String): Int = when {
        name.contains("printer") || name.contains("print") -> R.drawable.ic_printer
        name.contains("desktop") || name.contains("monitor") ||
            name.contains("laptop") || name.contains("computer") -> R.drawable.ic_desktop
        name.contains("light") || name.contains("bulb") || name.contains("lamp") ->
            R.drawable.ic_light
        name.contains("plug") || name.contains("socket") || name.contains("outlet") ->
            R.drawable.ic_plug
        name.contains("therm") || name.contains("temperature") -> R.drawable.ic_temp
        name.contains("toggle") -> R.drawable.ic_toggle
        name.contains("power") || name.contains("flash") || name.contains("lightning") ->
            R.drawable.ic_power
        else -> R.drawable.ic_switch
    }

    private fun name(icon: String?): String =
        icon?.removePrefix("mdi:")?.lowercase().orEmpty()

    /** Auswahl im Editor: mdi-Name → mitgeliefertes Symbol. */
    val choices: List<Pair<String, Int>> = listOf(
        "mdi:power" to R.drawable.ic_power,
        "mdi:toggle-switch" to R.drawable.ic_toggle,
        "mdi:lightbulb" to R.drawable.ic_light,
        "mdi:power-plug" to R.drawable.ic_plug,
        "mdi:printer-3d" to R.drawable.ic_printer,
        "mdi:desktop-tower" to R.drawable.ic_desktop,
        "mdi:thermometer" to R.drawable.ic_temp,
        "mdi:switch" to R.drawable.ic_switch,
    )
}
