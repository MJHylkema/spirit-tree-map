package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class AdventureLogComposite implements IMap
{
	final private int MENU_SETUP_SCRIPT_ID = 219;
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
	private void onScriptPreFired(ScriptPreFired ev)
	{
		switch (ev.getScriptId())
		{
			case MENU_SETUP_SCRIPT_ID:
			{
				String title = (String) client.getObjectStack()[client.getObjectStackSize() - 1];

				for (IAdventureMap map: this.adventureLogMaps)
				{
					if (!map.isActive())
						continue;

					boolean response = map.matchesTitle(title);

					if (response)
					{
						// To avoid the default adventure log list flashing on the screen briefly, always hide it upfront.
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

							setAdventureLogWidgetsHidden(new int[] {
								AdventureLog.CONTAINER
							}, false);

							map.buildInterface(adventureLogContainer);
						});
						break;
					}
				}
				break;
			}
			default:
				return;
		}
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
