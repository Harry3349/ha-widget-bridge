package de.reimann.hawidget.data

import org.json.JSONArray
import org.json.JSONObject

/** JSON-Umwandlung ohne Zusatzbibliotheken (org.json ist Teil von Android). */
object WidgetJson {

    // ---------------------------------------------------------------- lesen

    fun parseWidgets(body: String): List<WidgetDef> {
        val root = JSONObject(body)
        val array = root.optJSONArray("widgets") ?: return emptyList()
        val result = ArrayList<WidgetDef>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { result.add(parseWidget(it)) }
        }
        return result
    }

    fun parseWidget(json: JSONObject): WidgetDef = WidgetDef(
        id = json.optString("id"),
        name = json.optString("name"),
        template = json.stringOrNull("template"),
        values = parseValues(json.optJSONArray("values")),
        buttons = parseButtons(json.optJSONArray("buttons")),
        rows = parseRows(json.optJSONArray("rows")),
        theme = parseTheme(json.optJSONObject("theme")),
        textSize = json.optDouble("text_size", 14.0).toFloat(),
        valueColumns = json.optInt("value_columns", 1).coerceIn(1, 3),
        valueLabelAbove = json.optBoolean("value_label_above", false),
        revision = json.optInt("revision", 0),
    )

    fun parseSnapshot(body: String): WidgetSnapshot {
        val json = JSONObject(body)
        return WidgetSnapshot(
            id = json.optString("id"),
            name = json.optString("name"),
            revision = json.optInt("revision", 0),
            textSize = json.optDouble("text_size", 14.0).toFloat(),
            theme = parseTheme(json.optJSONObject("theme")),
            html = json.optString("html"),
            text = json.optString("text"),
            error = json.stringOrNull("error"),
            buttons = parseButtonStates(json.optJSONArray("buttons")),
            templateUsed = json.optBoolean("template_used", false),
            valueColumns = json.optInt("value_columns", 1).coerceIn(1, 3),
            valueLabelAbove = json.optBoolean("value_label_above", false),
            values = parseValueStates(json.optJSONArray("values")),
            rows = parseRows(json.optJSONArray("rows")),
        )
    }

    fun parseEntities(body: String): List<HaEntity> {
        val array = JSONArray(body)
        val result = ArrayList<HaEntity>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val entityId = json.optString("entity_id")
            if (entityId.isBlank()) continue
            val attributes = json.optJSONObject("attributes") ?: JSONObject()
            result.add(
                HaEntity(
                    entityId = entityId,
                    name = attributes.optString("friendly_name", entityId),
                    state = json.optString("state"),
                    unit = attributes.stringOrNull("unit_of_measurement"),
                    icon = attributes.stringOrNull("icon"),
                )
            )
        }
        return result
    }

    private fun parseValues(array: JSONArray?): List<WidgetValue> {
        if (array == null) return emptyList()
        val result = ArrayList<WidgetValue>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val entity = json.optString("entity")
            if (entity.isBlank()) continue
            val threshold = if (json.has("threshold") && !json.isNull("threshold")) {
                json.optDouble("threshold")
            } else {
                null
            }
            result.add(
                WidgetValue(
                    entity = entity,
                    label = json.stringOrNull("label"),
                    threshold = threshold,
                    color = json.stringOrNull("color"),
                )
            )
        }
        return result
    }

    private fun parseButtons(array: JSONArray?): List<WidgetButton> {
        if (array == null) return emptyList()
        val result = ArrayList<WidgetButton>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val key = json.optString("key")
            if (key.isBlank()) continue
            result.add(
                WidgetButton(
                    key = key,
                    label = json.optString("label", key),
                    icon = json.stringOrNull("icon"),
                    service = json.optString("service", "switch.toggle"),
                    entityId = json.stringOrNull("entity_id"),
                    stateEntity = json.stringOrNull("state_entity"),
                    stateLabelOn = json.stringOrNull("state_label_on"),
                    stateLabelOff = json.stringOrNull("state_label_off"),
                )
            )
        }
        return result
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
                    text = json.stringOrNull("text"),
                    entity = json.stringOrNull("entity"),
                    label = json.stringOrNull("label"),
                    color = json.stringOrNull("color"),
                    align = json.optString("align", "center"),
                    size = json.optDouble("size", 14.0).toFloat(),
                    threshold = if (json.has("threshold") && !json.isNull("threshold")) {
                        json.optDouble("threshold")
                    } else {
                        null
                    },
                    key = json.stringOrNull("key"),
                    icon = json.stringOrNull("icon"),
                    service = json.optString("service", "switch.toggle"),
                    entityId = json.stringOrNull("entity_id"),
                    stateEntity = json.stringOrNull("state_entity"),
                    stateLabelOn = json.stringOrNull("state_label_on"),
                    stateLabelOff = json.stringOrNull("state_label_off"),
                    stateLabel = json.stringOrNull("state_label"),
                    active = json.optBoolean("active", false),
                    available = json.optBoolean("available", true),
                )
            )
        }
        return result
    }

    private fun parseValueStates(array: JSONArray?): List<ValueState> {
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

    private fun parseButtonStates(array: JSONArray?): List<ButtonState> {        if (array == null) return emptyList()
        val result = ArrayList<ButtonState>(array.length())
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            result.add(
                ButtonState(
                    key = json.optString("key"),
                    label = json.optString("label"),
                    icon = json.stringOrNull("icon"),
                    stateLabel = json.optString("state_label"),
                    active = json.optBoolean("active", false),
                    available = json.optBoolean("available", false),
                )
            )
        }
        return result
    }

    private fun parseTheme(json: JSONObject?): WidgetTheme {
        if (json == null) return WidgetTheme()
        val defaults = WidgetTheme()
        return WidgetTheme(
            background = json.optString("background", defaults.background),
            textColor = json.optString("text_color", defaults.textColor),
            accent = json.optString("accent", defaults.accent),
            buttonBackground = json.optString("button_background", defaults.buttonBackground),
            buttonText = json.optString("button_text", defaults.buttonText),
        )
    }

    // -------------------------------------------------------------- schreiben

    fun toJson(def: WidgetDef): JSONObject {
        val json = JSONObject()
        json.put("id", def.id)
        json.put("name", def.name)
        if (!def.template.isNullOrBlank()) json.put("template", def.template)
        json.put("text_size", def.textSize.toDouble())
        json.put("value_columns", def.valueColumns.coerceIn(1, 3))
        json.put("value_label_above", def.valueLabelAbove)

        val values = JSONArray()
        def.values.forEach { value ->
            val item = JSONObject()
            item.put("entity", value.entity)
            value.label?.takeIf { it.isNotBlank() }?.let { item.put("label", it) }
            value.threshold?.let { item.put("threshold", it) }
            value.color?.takeIf { it.isNotBlank() }?.let { item.put("color", it) }
            values.put(item)
        }
        json.put("values", values)

        val buttons = JSONArray()
        def.buttons.forEach { button ->
            val item = JSONObject()
            item.put("key", button.key)
            item.put("label", button.label)
            item.put("service", button.service)
            button.icon?.takeIf { it.isNotBlank() }?.let { item.put("icon", it) }
            button.entityId?.takeIf { it.isNotBlank() }?.let { item.put("entity_id", it) }
            button.stateEntity?.takeIf { it.isNotBlank() }?.let { item.put("state_entity", it) }
            buttons.put(item)
        }
        json.put("buttons", buttons)

        // Zeilen-Layout (gilt für Handy-Widget und Uhr-Tile gleichzeitig)
        val rows = JSONArray()
        def.rows.forEach { row ->
            val matrix = JSONArray()
            row.items.forEach { item ->
                val entry = JSONObject()
                entry.put("type", item.type)
                entry.put("align", item.align)
                entry.put("size", item.size.toDouble())
                item.color?.takeIf { it.isNotBlank() }?.let { entry.put("color", it) }

                when (item.type) {
                    "sensor" -> {
                        entry.put("entity", item.entity.orEmpty())
                        item.label?.takeIf { it.isNotBlank() }?.let { entry.put("label", it) }
                        item.threshold?.let { entry.put("threshold", it) }
                    }

                    "button" -> {
                        item.key?.takeIf { it.isNotBlank() }?.let { entry.put("key", it) }
                        entry.put("label", item.label.orEmpty())
                        entry.put("service", item.service)
                        item.icon?.takeIf { it.isNotBlank() }?.let { entry.put("icon", it) }
                        item.entityId?.takeIf { it.isNotBlank() }
                            ?.let { entry.put("entity_id", it) }
                        item.stateEntity?.takeIf { it.isNotBlank() }
                            ?.let { entry.put("state_entity", it) }
                    }

                    else -> entry.put("text", item.text.orEmpty())
                }

                matrix.put(entry)
            }
            val rowJson = JSONObject()
            rowJson.put("items", matrix)
            rows.put(rowJson)
        }
        json.put("rows", rows)

        val theme = JSONObject()
        theme.put("background", def.theme.background)
        theme.put("text_color", def.theme.textColor)
        theme.put("accent", def.theme.accent)
        theme.put("button_background", def.theme.buttonBackground)
        theme.put("button_text", def.theme.buttonText)
        json.put("theme", theme)

        return json
    }

    /** Fehlermeldung aus einer HA-Antwort ziehen (``{"error": "..."}``). */
    fun errorOf(body: String): String? = runCatching {
        JSONObject(body).stringOrNull("error")
    }.getOrNull()

    private fun JSONObject.stringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        val value = optString(key, "")
        return value.ifBlank { null }
    }
}
