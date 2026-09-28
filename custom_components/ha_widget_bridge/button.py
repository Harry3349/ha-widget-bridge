"""Button-Entities je Widget-Button.

Jeder in der App definierte Button wird ein echter HA-Button. Dadurch lässt
sich dieselbe Aktion auch aus Automationen, Dashboards oder per Sprachbefehl
auslösen – und die App muss nur noch ``button.press`` aufrufen.
"""

from __future__ import annotations

from typing import Any

from homeassistant.components.button import ButtonEntity
from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant, callback
from homeassistant.helpers.device_registry import DeviceEntryType, DeviceInfo
from homeassistant.helpers.dispatcher import async_dispatcher_connect, async_dispatcher_send
from homeassistant.helpers.entity_platform import AddEntitiesCallback

from .actions import async_run_button
from .const import (
    DOMAIN,
    MANUFACTURER,
    MODEL,
    SIGNAL_WIDGET_RENDERED,
    SIGNAL_WIDGETS_UPDATED,
)
from .store import get_store


async def async_setup_entry(
    hass: HomeAssistant,
    entry: ConfigEntry,
    async_add_entities: AddEntitiesCallback,
) -> None:
    """Für jeden konfigurierten Button eine Entity anlegen."""
    store = get_store(hass)
    if store is None:
        return

    known: set[tuple[str, str]] = set()

    @callback
    def _sync_entities(_widget_id: str | None = None) -> None:
        new_entities: list[WidgetButton] = []
        for widget in store.as_list():
            for button in widget["buttons"]:
                ref = (widget["id"], button["key"])
                if ref not in known:
                    known.add(ref)
                    new_entities.append(WidgetButton(hass, widget["id"], button))
        if new_entities:
            async_add_entities(new_entities)

    _sync_entities()
    entry.async_on_unload(
        async_dispatcher_connect(hass, SIGNAL_WIDGETS_UPDATED, _sync_entities)
    )


class WidgetButton(ButtonEntity):
    """Ein Button eines Android-Widgets."""

    _attr_should_poll = False
    _attr_has_entity_name = True

    def __init__(self, hass: HomeAssistant, widget_id: str, button: dict[str, Any]) -> None:
        self.hass = hass
        self._widget_id = widget_id
        self._key = button["key"]
        self._attr_unique_id = f"{widget_id}_btn_{self._key}"
        # Name und Icon müssen schon hier gesetzt sein: Die Entity-ID leitet
        # Home Assistant beim Hinzufügen aus dem Namen ab.
        self._attr_name = button["label"]
        self._attr_icon = button.get("icon")
        self._widget: dict[str, Any] | None = None
        self._button: dict[str, Any] | None = button

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
        )

    @property
    def available(self) -> bool:
        return self._button is not None

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        button = self._button or {}
        return {
            "widget_id": self._widget_id,
            "key": self._key,
            "service": button.get("service"),
            "entity_id": button.get("entity_id"),
            "state_entity": button.get("state_entity"),
        }

    # -- Lebenszyklus -----------------------------------------------------
    async def async_added_to_hass(self) -> None:
        self._reload()
        if self._button is None:
            self.hass.async_create_task(self.async_remove())
            return
        self.async_on_remove(
            async_dispatcher_connect(
                self.hass, SIGNAL_WIDGETS_UPDATED, self._handle_definition_changed
            )
        )
    @callback
    def _handle_definition_changed(self, widget_id: str | None = None) -> None:
        if widget_id and widget_id != self._widget_id:
            return
        self._reload()
        if self._button is None:
            self.hass.async_create_task(self.async_remove())
            return
        self.async_write_ha_state()

    @callback
    def _reload(self) -> None:
        store = get_store(self.hass)
        self._widget = store.get(self._widget_id) if store else None
        self._button = next(
            (
                item
                for item in (self._widget or {}).get("buttons", [])
                if item["key"] == self._key
            ),
            None,
        )
        self._attr_name = self._button["label"] if self._button else self._key
        self._attr_icon = self._button.get("icon") if self._button else None

    # -- Aktion -----------------------------------------------------------
    async def async_press(self) -> None:
        if self._button is None:
            return
        await async_run_button(self.hass, self._button, context=self._context)
        # Widget-Inhalt direkt neu rendern (z. B. aktualisierte Leistung)
        async_dispatcher_send(self.hass, SIGNAL_WIDGET_RENDERED, self._widget_id)
