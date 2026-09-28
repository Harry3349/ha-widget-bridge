"""Sensor-Entity je Widget: der gerenderte Inhalt.

Damit ist das Widget auch in Home Assistant sichtbar (Verlauf, Automationen,
Dashboard) – die Android-App zeigt genau denselben Inhalt an.
"""

from __future__ import annotations

from datetime import timedelta
from typing import Any

from homeassistant.components.sensor import SensorEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceEntryType, DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect
from homeassistant.helpers.entity_platform import AddEntitiesCallback
from homeassistant.helpers.event import async_track_time_interval

from .const import (
    ATTR_ERROR,
    ATTR_HTML,
    ATTR_REVISION,
    ATTR_UPDATED_AT,
    ATTR_WIDGET_ID,
    CONTENT_REFRESH_SECONDS,
    DOMAIN,
    MANUFACTURER,
    MODEL,
    SIGNAL_WIDGET_RENDERED,
    SIGNAL_WIDGETS_UPDATED,
)
from .render import async_render_widget
from .store import get_store


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Für jedes gespeicherte Widget einen Inhalts-Sensor anlegen."""
    store = get_store(hass)
    if store is None:
        return

    known: set[str] = set()

    @callback
    def _sync_entities(_widget_id: str | None = None) -> None:
        new_entities: list[WidgetContentSensor] = []
        for widget in store.as_list():
            if widget["id"] not in known:
                known.add(widget["id"])
                new_entities.append(WidgetContentSensor(hass, widget["id"]))
        if new_entities:
            async_add_entities(new_entities)

    _sync_entities()
    entry.async_on_unload(
        async_dispatcher_connect(hass, SIGNAL_WIDGETS_UPDATED, _sync_entities)
    )


class WidgetContentSensor(SensorEntity):
    """Zeigt den gerenderten Inhalt eines Widgets (Klartext + HTML im Attribut)."""

    _attr_should_poll = False
    _attr_has_entity_name = True
    _attr_icon = "mdi:cellphone"

    def __init__(self, hass: HomeAssistant, widget_id: str) -> None:
        self.hass = hass
        self._widget_id = widget_id
        self._attr_unique_id = f"{widget_id}_content"
        self._attr_name = "Inhalt"
        self._attr_native_value: str | None = None
        self._widget: dict[str, Any] | None = None
        self._html: str = ""
        self._error: str | None = None

    # -- Anzeige ----------------------------------------------------------
    @property
    def device_info(self) -> DeviceInfo | None:
        widget = self._widget
        if widget is None:
            return None
        return DeviceInfo(
            identifiers={(DOMAIN, widget["id"])},
            name=widget["name"],
            manufacturer=MANUFACTURER,
            model=MODEL,
            entry_type=DeviceEntryType.SERVICE,
            configuration_url="/config/integrations/integration/ha_widget_bridge",
        )

    @property
    def available(self) -> bool:
        return self._widget is not None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        widget = self._widget or {}
        attributes: dict[str, Any] = {
            ATTR_WIDGET_ID: widget.get("id"),
            ATTR_HTML: self._html,
            ATTR_REVISION: widget.get("revision"),
            ATTR_UPDATED_AT: widget.get("updated_at"),
            "values": [item["entity"] for item in widget.get("values", [])],
            "buttons": [item["key"] for item in widget.get("buttons", [])],
        }
        if self._error:
            attributes[ATTR_ERROR] = self._error
        return attributes

    # -- Lebenszyklus -----------------------------------------------------
    async def async_added_to_hass(self) -> None:
        store = get_store(self.hass)
        self._widget = store.get(self._widget_id) if store else None

        if self._widget is None:
            self.hass.async_create_task(self.async_remove())
            return

        self.async_on_remove(
            async_dispatcher_connect(
                self.hass, SIGNAL_WIDGETS_UPDATED, self._handle_definition_changed
            )
        )
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass, SIGNAL_WIDGET_RENDERED, self._handle_render_requested
            )
        )
        self.async_on_remove(
            async_track_time_interval(
                self.hass,
                self._async_tick,
                timedelta(seconds=CONTENT_REFRESH_SECONDS),
            )
        )

        await self._async_render()

    # -- Callbacks --------------------------------------------------------
    @callback
    def _handle_definition_changed(self, widget_id: str | None = None) -> None:
        if widget_id and widget_id != self._widget_id:
            return

        store = get_store(self.hass)
        widget = store.get(self._widget_id) if store else None

        if widget is None:
            self._widget = None
            self.hass.async_create_task(self.async_remove())
            return

        self._widget = widget
        self.hass.async_create_task(self._async_render())

    @callback
    def _handle_render_requested(self, widget_id: str | None = None) -> None:
        if widget_id and widget_id != self._widget_id:
            return
        self.hass.async_create_task(self._async_render())

    async def _async_tick(self, _now: Any = None) -> None:
        await self._async_render()

    # -- Rendern ----------------------------------------------------------
    async def _async_render(self) -> None:
        if self._widget is None:
            return

        rendered = await async_render_widget(self.hass, self._widget)
        self._html = rendered["html"]
        self._error = rendered["error"]

        value = self._error or rendered["text"] or "(kein Inhalt)"
        self._attr_native_value = value[:255]
        self.async_write_ha_state()
