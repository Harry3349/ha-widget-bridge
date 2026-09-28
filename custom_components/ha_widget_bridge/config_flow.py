"""Config Flow – ein einziger Eintrag verwaltet alle Widgets."""

from __future__ import annotations

from typing import Any

from homeassistant.config_entries import ConfigFlow, ConfigFlowResult

from .const import DOMAIN, NAME


class HaWidgetBridgeConfigFlow(ConfigFlow, domain=DOMAIN):
    """Einrichtungsdialog: nur bestätigen, die Widgets kommen aus der App."""

    VERSION = 1

    async def async_step_user(self, user_input: dict[str, Any] | None = None) -> ConfigFlowResult:
        await self.async_set_unique_id(DOMAIN)
        self._abort_if_unique_id_configured()

        if user_input is not None:
            return self.async_create_entry(title=NAME, data={})

        return self.async_show_form(step_id="user")

    async def async_step_import(self, import_info: dict[str, Any]) -> ConfigFlowResult:
        """Import aus configuration.yaml (unterstützt ``ha_widget_bridge:``)."""
        return await self.async_step_user(import_info)
