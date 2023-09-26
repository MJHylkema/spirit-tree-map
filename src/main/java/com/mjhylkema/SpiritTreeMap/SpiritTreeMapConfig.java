package com.mjhylkema.SpiritTreeMap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("spirittreemap")
public interface SpiritTreeMapConfig extends Config
{
	String KEY_DISPLAY_HOTKEYS = "displayHotkeys";

	@ConfigItem(
		keyName = KEY_DISPLAY_HOTKEYS,
		name = "Display hotkeys",
		description = "Display the travel keyboard hotkey for each tree"
	)
	default boolean displayHotkeys()
	{
		return true;
	}
}
