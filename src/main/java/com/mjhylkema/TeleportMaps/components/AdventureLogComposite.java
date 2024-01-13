package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;

public class AdventureLogComposite implements IMap
{
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	static class AdventureLog
	{
		static final int CONTAINER = 0;
		static final int EVENT_LISTENER_LIST = 1;
		static final int SCROLLBAR = 2;
		static final int LIST = 3;
		static final int CLOSE_BUTTON = 4;
	}

	private List<IMap> adventureLogMaps;
	private TeleportMapsPlugin plugin;

	public AdventureLogComposite(TeleportMapsPlugin plugin)
	{
		this.plugin = plugin;
		this.adventureLogMaps = new ArrayList<>();
	}

	public void addAdventureLogMap(IMap map)
	{
		this.adventureLogMaps.add(map);
	}

	@Override
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() != InterfaceID.ADVENTURE_LOG)
			return;

		if (!this.isActive())
			return;

		// To avoid the default adventure log list flashing on the screen briefly, always hide it upfront.
		// These widgets will be un-hidden in the invokeLater if it's not the "Spirit Tree Locations".
		setAdventureLogWidgetsHidden(new int[] {
			AdventureLog.CONTAINER,
			AdventureLog.LIST,
			AdventureLog.SCROLLBAR
		}, true);

		this.plugin.getClientThread().invokeLater(() ->
		{
			Widget adventureLogContainer = this.plugin.getClient().getWidget(WidgetInfo.ADVENTURE_LOG);
			if (adventureLogContainer == null)
				return;

			Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
			if (title == null)
				return;

			boolean response = false;
			for (IMap map: this.adventureLogMaps)
			{
				response = ((IAdventureMap)map).isActiveWidget(title.getText());
				if (response)
				{
					map.onWidgetLoaded(e);
					break;
				}
			}

			if (!response)
				setAdventureLogWidgetsHidden(new int[] {
					AdventureLog.CONTAINER,
					AdventureLog.LIST,
					AdventureLog.SCROLLBAR
				}, false);
			else
				setAdventureLogWidgetsHidden(new int[] {
					AdventureLog.CONTAINER
				}, false);
		});
	}

	@Override
	public void changeHotkeyVisibility(boolean visible)
	{
		this.adventureLogMaps.forEach((map -> map.changeHotkeyVisibility(visible)));
	}

	@Override
	public void setActive(String key, boolean active)
	{
		this.adventureLogMaps.forEach((map -> map.setActive(key, active)));
	}

	@Override
	public boolean isActive()
	{
		for (IMap map: this.adventureLogMaps)
		{
			if (map.isActive())
				return true;
		}
		return false;
	}

	private void setAdventureLogWidgetsHidden(int[] childIds, boolean hidden)
	{
		this.setWidgetsHidden(InterfaceID.ADVENTURE_LOG, childIds, hidden);
	}

	protected void setWidgetsHidden(int groupID, int[] childIDs, boolean hidden)
	{
		for(int childId : childIDs)
		{
			Widget widget = this.plugin.getClient().getWidget(groupID, childId);
			if (widget != null)
			{
				widget.setHidden(hidden);
			}
		}
	}
}
