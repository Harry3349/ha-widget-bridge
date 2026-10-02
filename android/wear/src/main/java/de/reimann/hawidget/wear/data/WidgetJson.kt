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
        )
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
                    icon = json.optString("icon").takeIf { it.isNotBlank() },
                )
            )
        }
        return result
    }
}
