package de.reimann.hawidget.wear.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON-Umwandlung ohne Zusatzbibliotheken (org.json ist Teil von Android). */
object WidgetJson {

    fun parseSnapshot(body: String): WidgetSnapshot {
        val json = JSONObject(body)
        return WidgetSnapshot(
            id = json.optString("id"),
            name = json.optString("name"),
            revision = json.optInt("revision", 0),
            updatedAt = json.optString("updated_at"),
            values = parseValues(json.optJSONArray("values")),
            buttons = parseButtons(json.optJSONArray("buttons")),
            error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
            valueColumns = json.optInt("value_columns", 1).coerceIn(1, 3),
            valueLabelAbove = json.optBoolean("value_label_above", false),
            rows = parseRows(json.optJSONArray("rows")),
            watchRows = json.optInt("watch_rows", 0).coerceIn(0, 8),
            watchScale = json.optDouble("watch_scale", 1.0).toFloat().coerceIn(0.6f, 1.8f),
        )
    }

    private fun parseRows(array: JSONArray?): List<RowDef> {
        if (array == null) return emptyList()
        val result = ArrayList<RowDef>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            result.add(RowDef(items = parseRowItems(json.optJSONArray("items"))))
        }
        return result
    }

    private fun parseRowItems(array: JSONArray?): List<RowItem> {
        if (array == null) return emptyList()
        val result = ArrayList<RowItem>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            result.add(
                RowItem(
                    type = json.optString("type", "text"),
                    text = json.optString("text").takeIf { it.isNotBlank() },
                    entity = json.optString("entity").takeIf { it.isNotBlank() },
                    label = json.optString("label").takeIf { it.isNotBlank() },
                    color = json.optString("color").takeIf { it.isNotBlank() },
                    align = json.optString("align", "center"),
                    size = json.optDouble("size", 14.0).toFloat(),
                    width = json.optDouble("width", 0.0).toFloat().coerceIn(0f, 100f),
                    showState = json.optBoolean("show_state", true),
                    labelAbove = json.optBoolean("label_above", false),
                    key = json.optString("key").takeIf { it.isNotBlank() },
                    icon = json.optString("icon").takeIf { it.isNotBlank() },
                    stateLabel = json.optString("state_label").takeIf { it.isNotBlank() },
                    active = json.optBoolean("active", false),
                    available = json.optBoolean("available", true),
                )
            )
        }
        return result
    }

    fun firstWidgetId(body: String): String? {
        val array = JSONObject(body).optJSONArray("widgets") ?: return null
        for (index in 0 until array.length()) {
            val id = array.optJSONObject(index)?.optString("id").orEmpty()
            if (id.isNotBlank()) return id
        }
        return null
    }

    private fun parseValues(array: JSONArray?): List<ValueState> {
        if (array == null) return emptyList()
        val result = ArrayList<ValueState>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            result.add(
                ValueState(
                    entity = json.optString("entity"),
                    label = json.optString("label", json.optString("entity")),
                    text = json.optString("text"),
                    color = json.optString("color"),
                    active = json.optBoolean("active", false),
                    available = json.optBoolean("available", true),
                )
            )
        }
        return result
    }

    private fun parseButtons(array: JSONArray?): List<ButtonState> {
        if (array == null) return emptyList()
        val result = ArrayList<ButtonState>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val key = json.optString("key")
            if (key.isBlank()) continue
            result.add(
                ButtonState(
                    key = key,
                    label = json.optString("label", key),
                    active = json.optBoolean("active", false),
                    available = json.optBoolean("available", true),
                    stateLabel = json.optString("state_label"),
                    showState = json.optBoolean("show_state", true),
                    icon = json.optString("icon").takeIf { it.isNotBlank() },
                )
            )
        }
        return result
    }
}
