package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import java.util.List;
import net.runelite.api.widgets.Widget;

public interface IAdventureMap extends IMap
{
	boolean matchesTitle(String title);

	/**
	 * Identifies a menu by its entries, for variants of a menu whose title
	 * differs, such as the POH versions of a teleport. Only consulted when
	 * no map matches the menu's title.
	 * @param entryNames the names of the menu's entries
	 */
	default boolean matchesEntries(List<String> entryNames)
	{
		return false;
	}

	/**
	 * Replaces an open teleport menu with this map.
	 * @param container the widget the map is built into
	 * @param entryList the widget whose dynamic children are the menu's entry labels
	 */
	void buildInterface(Widget container, Widget entryList);
}
