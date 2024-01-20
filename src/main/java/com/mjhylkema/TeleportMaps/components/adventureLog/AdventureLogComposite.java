package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

public class AdventureLogComposite implements IMap
{
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	static class AdventureLog
	{
		static final int CONTAINER = 0;
		static final int EVENT_LISTENER_LIST = 1;
		static final int SCROLLBAR = 2;
		static final int LIST = 3;
		static final int CLOSE_BUTTON = 4;
	}

	final private List<IAdventureMap> adventureLogMaps;
	final private Client client;
	final private ClientThread clientThread;

	@Inject
	public AdventureLogComposite(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.adventureLogMaps = new ArrayList<>();
	}

	public void addAdventureLogMap(IAdventureMap map)
	{
		this.adventureLogMaps.add(map);
	}

	@Subscribe
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

		this.clientThread.invokeLater(() ->
		{
			Widget adventureLogContainer = this.client.getWidget(ComponentID.ADVENTURE_LOG_CONTAINER);
			if (adventureLogContainer == null)
				return;

			Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
			if (title == null)
				return;

			boolean response = false;
			for (IAdventureMap map: this.adventureLogMaps)
			{
				if (!map.isActive())
					continue;

				response = map.matchesTitle(title.getText());
				if (response)
				{
					map.buildInterface(adventureLogContainer);
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
	public boolean isActive()
	{
		for (IAdventureMap map : this.adventureLogMaps)
		{
			if (map.isActive())
				return true;
		}
		return false;
	}

	protected void setAdventureLogWidgetsHidden(int[] childIDs, boolean hidden)
	{
		for(int childId : childIDs)
		{
			Widget widget = this.client.getWidget(InterfaceID.ADVENTURE_LOG, childId);
			if (widget != null)
			{
				widget.setHidden(hidden);
			}
		}
	}
}
