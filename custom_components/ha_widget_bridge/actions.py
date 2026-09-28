"""Ausführen der in einem Widget definierten Aktionen."""

from __future__ import annotations

from typing import Any

from homeassistant.core import Context, HomeAssistant
from homeassistant.exceptions import HomeAssistantError, ServiceNotFound

from .const import LOGGER


async def async_run_button(
    hass: HomeAssistant, button: dict[str, Any], context: Context | None = None
) -> None:
    """Den zu einem Button gehörenden Service aufrufen."""
    service: str = button["service"]
    domain, _, service_name = service.partition(".")

    if not hass.services.has_service(domain, service_name):
        raise ServiceNotFound(domain, service_name)

    data: dict[str, Any] = dict(button.get("service_data") or {})
    entity_id = button.get("entity_id")
    if entity_id and not any(key in data for key in ("entity_id", "target", "device_id", "area_id")):
        data["entity_id"] = entity_id

    LOGGER.debug("Widget-Button '%s': %s mit %s", button.get("key"), service, data)

    try:
        await hass.services.async_call(
            domain,
            service_name,
            data,
            blocking=True,
            context=context,
        )
    except HomeAssistantError as err:
        LOGGER.error("Widget-Button '%s' (%s) fehlgeschlagen: %s", button.get("key"), service, err)
        raise
