package de.reimann.hawidget.ui

import de.reimann.hawidget.data.RowDef
import de.reimann.hawidget.data.RowItem

/**
 * Schätzt ab, wie viele Zeilen auf Handy und Uhr sichtbar sind.
 *
 * RemoteViews kann die tatsächliche Widget-Höhe zur Bauzeit nicht kennen und die
 * Uhr zeigt nur eine runde Fläche ohne Größenangabe. Deshalb arbeiten die
 * Hinweise im Editor mit Erfahrungswerten (dp) – sie sollen nur zeigen, ab
 * welcher Zeile die Fläche zu klein wird.
 */
object RowCapacity {

    /** Nutzbare Inhaltshöhe eines Widgets auf dem Handy (ca. 2 Reihen hoch). */
    const val PHONE_SMALL_DP = 105f

    /** ca. 3 Reihen hoch */
    const val PHONE_MEDIUM_DP = 180f

    /** ca. 4 Reihen hoch */
    const val PHONE_LARGE_DP = 255f

    /** Runde Uhranzeige: etwa so viel Inhalt ist gleichzeitig sichtbar. */
    const val WATCH_DP = 150f

    /** Höhe einer Zeile abschätzen (Objekt mit der größten Schrift bestimmt sie). */
    fun rowHeightDp(items: List<RowItem>): Float {
        if (items.isEmpty()) return 0f
        var height = 16f
        items.forEach { item ->
            val size = item.size.coerceIn(8f, 30f)
            val own = when (item.type) {
                "button" -> 26f
                // „Wert unter dem Titel“ = eine zweite (kleinere) Zeile
                "sensor" ->
                    if (item.labelAbove && !item.label.isNullOrBlank()) {
                        size * 1.45f + (size - 3f).coerceAtLeast(9f) * 1.45f + 2f
                    } else {
                        size * 1.45f + 2f
                    }
                else -> size * 1.45f
            }
            if (own > height) height = own
        }
        return height + 4f
    }

    fun heightDp(rows: List<List<RowItem>>): Float =
        rows.fold(0f) { sum, row -> sum + rowHeightDp(row) }

    /** Wie viele Zeilen passen in die angegebene Höhe? */
    fun fittingRows(rows: List<List<RowItem>>, availableDp: Float): Int {
        var used = 0f
        var count = 0
        for (row in rows) {
            val next = used + rowHeightDp(row)
            if (next > availableDp) break
            used = next
            count++
        }
        return count
    }

    /** Ab dieser Zeile (1-basiert) passt das Handy-Widget nicht mehr in 2×2. */
    fun phoneGrowFrom(rows: List<List<RowItem>>): Int? {
        if (rows.isEmpty()) return null
        val fitting = fittingRows(rows, PHONE_SMALL_DP)
        return if (fitting >= rows.size) null else fitting + 1
    }

    /** Ab dieser Zeile (1-basiert) muss auf der Uhr gescrollt werden. */
    fun watchScrollFrom(rows: List<List<RowItem>>): Int? {
        if (rows.isEmpty()) return null
        val fitting = fittingRows(rows, WATCH_DP)
        return if (fitting >= rows.size) null else fitting + 1
    }

    /**
     * Index (0-basiert) der ersten Zeile, die auf der Kachel nicht mehr erscheint.
     *
     * Berücksichtigt – falls gesetzt – die feste Zahl an Zeilen auf der Kachel.
     */
    fun watchCutIndex(rows: List<RowDef>, fixedRows: Int): Int? {
        if (rows.isEmpty()) return null
        val items = rows.map { it.items }
        val fitting = if (fixedRows > 0) fixedRows else fittingRows(items, WATCH_DP)
        return if (fitting >= rows.size) null else fitting
    }

    /** Wie viele Zeilen zeigt die Kachel? */
    fun watchVisibleRows(rows: List<RowDef>, fixedRows: Int): Int {
        val items = rows.map { it.items }
        if (items.isEmpty()) return 0
        return if (fixedRows > 0) minOf(fixedRows, items.size) else fittingRows(items, WATCH_DP)
    }

    /** Hinweistext für das Handy-Widget. */
    fun phoneHint(rows: List<List<RowItem>>): String {
        if (rows.isEmpty()) return "Handy: noch keine Zeile angelegt."
        val small = fittingRows(rows, PHONE_SMALL_DP)
        val medium = fittingRows(rows, PHONE_MEDIUM_DP)
        val large = fittingRows(rows, PHONE_LARGE_DP)

        val text = when {
            small >= rows.size ->
                "alle ${rows.size} Zeilen passen in ein Widget mit etwa 2 Reihen Höhe."

            medium >= rows.size ->
                "Zeile 1–$small passen in etwa 2 Reihen Höhe; ab Zeile ${small + 1} das " +
                    "Widget höher ziehen (bis Zeile $medium reichen etwa 3 Reihen)."

            large >= rows.size ->
                "Zeile 1–$medium passen in etwa 3 Reihen Höhe; ab Zeile ${medium + 1} das " +
                    "Widget höher ziehen (bis Zeile $large reichen etwa 4 Reihen)."

            else ->
                "ab Zeile ${large + 1} ist selbst ein Widget mit 4 Reihen Höhe zu klein – " +
                    "dort besser eine zweite Zeile oder ein zweites Widget nutzen."
        }
        return "Handy: $text"
    }

    /** Hinweistext für die Uhr. */
    fun watchHint(rows: List<RowDef>, fixedRows: Int): String {
        if (rows.isEmpty()) {
            return "Uhr: noch keine Zeile angelegt – auf der Uhr erscheint nichts."
        }
        val visible = watchVisibleRows(rows, fixedRows)
        val fixed = if (fixedRows > 0) {
            "So eingestellt: die Kachel zeigt genau $fixedRows Zeile(n)."
        } else {
            "Einstellung „automatisch“: die Kachel zeigt so viele Zeilen, wie hineinpassen."
        }
        return if (visible >= rows.size) {
            "Uhr: alle ${rows.size} Zeilen passen auf die Kachel. $fixed"
        } else {
            "Uhr: ${rows.size} Zeilen angelegt, sichtbar sind $visible. Die übrigen zeigt " +
                "die App auf der Uhr (Kachel antippen) – dort lässt sich mit Wischen oder " +
                "Krone scrollen. $fixed"
        }
    }
}
