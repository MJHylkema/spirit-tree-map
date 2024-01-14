package com.mjhylkema.TeleportMaps.components;

import net.runelite.api.widgets.Widget;

public interface IAdventureMap extends IMap
{
	boolean matchesTitle(String title);
	void buildInterface(Widget adventureLogContainer);
}
