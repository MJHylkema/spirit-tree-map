package com.mjhylkema.TeleportMaps.components;

import net.runelite.api.events.WidgetLoaded;

public interface IMap
{
	void onWidgetLoaded(WidgetLoaded e);
	void changeHotkeyVisibility(boolean visible);
	void setActive(String key, boolean active);
	boolean isActive();
}
