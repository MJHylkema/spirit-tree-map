package com.mjhylkema.TeleportMaps;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("teleportmaps")
public interface TeleportMapsConfig extends Config
{
	String KEY_DISPLAY_HOTKEYS = "displayHotkeys";
	String KEY_SHOW_SPIRIT_TREE_MAP = "showSpiritTreeMap";
	String KEY_SHOW_MUSHTREE_MAP = "showMushtreeMap";
	String KEY_SHOW_XERICS_MAP = "showXericsMap";
	String KEY_SHOW_XERICS_MAP_LABELS = "showXericsMapLabels";
	String KEY_SHOW_XERICS_MAP_HOTKEY_LABELS = "showXericsMapHotkeyInLabels";

	@ConfigSection(
		name = "Teleport Maps",
		description = "Toggle on/off the supported maps",
		position = 0
	)
	String teleportMaps = "teleportMaps";

	@ConfigSection(
		name = "Xeric's Map Settings",
		description = "Settings related to Xeric's Talisman Map",
		position = 1
	)
	String xericsMap = "xericsMap";

	@ConfigSection(
		name = "General Settings",
		description = "Settings that apply to all maps",
		position = 2
	)
	String generalSettings = "generalSettings";

	@ConfigItem(
		keyName = KEY_SHOW_SPIRIT_TREE_MAP,
		name = "Spirit Tree Map",
		description = "Replace the Spirit Tree travel menu with an interactive map",
		section = teleportMaps
	)
	default boolean showSpiritTreeMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_MUSHTREE_MAP,
		name = "Fossil Island Mushtree Map",
		description = "Replace the Fossil Island Mushtree travel menu with an interactive map",
		section = teleportMaps
	)
	default boolean showMushtreeMap()
	{
		return true;
	}
	@ConfigItem(
		keyName = KEY_SHOW_XERICS_MAP,
		name = "Xeric's Talisman Map",
		description = "Replace Xeric's talisman travel menu with an interactive map",
		section = teleportMaps
	)
	default boolean showXericsMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_XERICS_MAP_LABELS,
		name = "Display labels",
		description = "Show named labels above each of the Xeric's Talisman teleport locations",
		section = xericsMap,
		position = 1
	)
	default boolean showXericsMapLabels()
	{
		return true;
	}

	@ConfigItem(
		keyName = KEY_SHOW_XERICS_MAP_HOTKEY_LABELS,
		name = "Show hotkey in labels",
		description = "Display the teleport hotkeys as part of the label, rather than hotkey icons",
		section = xericsMap,
		position = 2
	)
	default boolean showXericsMapHotkeyInLabels()
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
