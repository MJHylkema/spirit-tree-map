package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import net.runelite.api.events.WidgetLoaded;

public abstract class BaseMap
{
	protected TeleportMapsPlugin plugin;

	BaseMap(TeleportMapsPlugin plugin)
	{
		this.plugin = plugin;
	}

	public abstract void widgetLoaded(WidgetLoaded e);

	public abstract void changeHotkeyLabelVisibility(boolean visible);

	public abstract void changeLabelVisibility();
}
