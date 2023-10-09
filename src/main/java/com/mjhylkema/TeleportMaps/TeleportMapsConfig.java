package com.mjhylkema.TeleportMaps;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("teleportmaps")
public interface TeleportMapsConfig extends Config
{
	String KEY_DISPLAY_HOTKEYS = "displayHotkeys";

	@ConfigSection(
		name = "Teleport Maps",
		description = "Toggle on/off the supported maps",
		position = 0
	)
	String teleportMaps = "teleportMaps";

	@ConfigSection(
		name = "General Settings",
		description = "Settings that apply to all maps",
		position = 0
	)
	String generalSettings = "generalSettings";

	@ConfigItem(
		keyName = KEY_DISPLAY_HOTKEYS,
		name = "Spirit Tree Map",
		description = "Replace the Spirit Tree travel menu with an interactive map",
		section = teleportMaps
	)
	default boolean showSpiritTreeMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_DISPLAY_HOTKEYS,
		name = "Display hotkeys",
		description = "Display the travel keyboard hotkey for each map location",
		section = generalSettings
	)
	default boolean displayHotkeys()
	{
		return true;
	}
}
