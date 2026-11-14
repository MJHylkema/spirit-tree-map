package com.mjhylkema.TeleportMaps.components.chatOptions;

import com.mjhylkema.TeleportMaps.components.IMap;
import java.util.List;
import net.runelite.api.widgets.Widget;

public interface IChatOptionsMap extends IMap
{
	/**
	 * @param title the dialog title with formatting tags removed, or null if it has none
	 * @param options the dialog's selectable options
	 */
	boolean matchesDialog(String title, List<ChatOption> options);

	/**
	 * Replaces an open chat options dialog with this map.
	 * @param container the screen layer the map is built into
	 * @param x the x position of the map frame within the container
	 * @param y the y position of the map frame within the container
	 * @param options the dialog's selectable options
	 */
	void buildInterface(Widget container, int x, int y, List<ChatOption> options);

	/**
	 * Releases references to the map's widgets once they've been removed
	 */
	void clearInterface();
}
