package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.components.IMap;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
public class AdventureLogComposite implements IMap
{
	/* Setup scripts of the classic scroll menu (interface 187) and its
	   "Modern menu interfaces" replacement (interface 947) */
	final private int MENU_SETUP_SCRIPT_ID = 219;
	final private int NEW_MENU_SETUP_SCRIPT_ID = 9142;

	// The modern menu's group id, absent from the deprecated InterfaceID class
	private static final int MENU_NEW = net.runelite.api.gameval.InterfaceID.MENU_NEW;

	/* Matches an entry label on either menu, e.g. "<col=735a28>1</col>: Varrock";
	   group 2 marks the classic menu's locked entries */
	public static final String ENTRY_LABEL_PATTERN = "<col=(?:735a28|ffffff)>(.+)</col>: (<col=5f5f5f>)?(.+)";

	static class AdventureLog
	{
		static final int CONTAINER = 0;
		static final int EVENT_LISTENER_LIST = 1;
		static final int SCROLLBAR = 2;
		static final int LIST = 3;
		static final int CLOSE_BUTTON = 4;
	}

	/* Children of the modern menu. Maps build into INFINITE, its 512x334 root
	   layer matching the classic build target; UNIVERSE shrinks to fit the
	   entry list and clips to those bounds, so the map can't go there. */
	static class NewMenu
	{
		static final int INFINITE = 0;
		static final int UNIVERSE = 1;
		static final int FRAME = 2;
		static final int TITLE = 3;
		static final int CONTENT_FRAME = 4;
		static final int CONTENT_SCROLL = 7;
		static final int TEXT = 9;
		static final int SCROLLBAR = 10;
	}

	// Replaces the modern menu's close button, which is hidden with its title bar
	private static final int IF_CLOSE_SCRIPT_ID = 29;
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGHT = 23;
	private static final int CLOSE_BUTTON_X = 428;
	private static final int CLOSE_BUTTON_Y = 22;

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
							Widget entryList = this.client.getWidget(InterfaceID.ADVENTURE_LOG, AdventureLog.LIST);
							if (adventureLogContainer == null || entryList == null)
								return;

							setAdventureLogWidgetsHidden(new int[] {
								AdventureLog.CONTAINER
							}, false);

							map.buildInterface(adventureLogContainer, entryList);
						});
						break;
					}
				}
				break;
			}
			case NEW_MENU_SETUP_SCRIPT_ID:
			{
				String title = this.getNewMenuTitle(ev);
				if (title == null)
					return;

				for (IAdventureMap map: this.adventureLogMaps)
				{
					if (!map.isActive())
						continue;

					if (map.matchesTitle(title))
					{
						// Hide the menu upfront so it can't flash on screen
						setNewMenuWidgetsHidden(new int[] {
							NewMenu.UNIVERSE
						}, true);

						this.clientThread.invokeLater(() ->
						{
							Widget root = this.client.getWidget(MENU_NEW, NewMenu.INFINITE);
							Widget entryList = this.client.getWidget(MENU_NEW, NewMenu.TEXT);
							if (root == null || entryList == null)
							{
								// Restore the native menu rather than leaving it hidden
								setNewMenuWidgetsHidden(new int[] {
									NewMenu.UNIVERSE
								}, false);
								return;
							}

							// Hide the menu's visuals but keep the layers holding
							// the entry key listeners shown, as the native hotkeys
							// only work while they're visible
							setNewMenuWidgetsHidden(new int[] {
								NewMenu.FRAME,
								NewMenu.TITLE,
								NewMenu.CONTENT_SCROLL,
								NewMenu.SCROLLBAR
							}, true);
							this.hideContentFrameBorder();

							// The setup script may re-run in an already open menu
							root.deleteAllChildren();

							map.buildInterface(root, entryList);
							this.createNewMenuCloseButton(root);

							setNewMenuWidgetsHidden(new int[] {
								NewMenu.UNIVERSE
							}, false);
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

	/**
	 * The modern menu's setup script receives its title as its first argument
	 * (following the script id) rather than on the string stack.
	 */
	private String getNewMenuTitle(ScriptPreFired ev)
	{
		Object[] arguments = ev.getScriptEvent() == null ? null : ev.getScriptEvent().getArguments();

		if (arguments == null || arguments.length <= 1 || !(arguments[1] instanceof String))
			return null;

		return (String) arguments[1];
	}

	/**
	 * Hides the border art the content frame draws around the entry list.
	 */
	private void hideContentFrameBorder()
	{
		Widget contentFrame = this.client.getWidget(MENU_NEW, NewMenu.CONTENT_FRAME);
		if (contentFrame == null)
			return;

		for (Widget child : contentFrame.getDynamicChildren())
		{
			child.setHidden(true);
		}
	}

	/**
	 * Creates a close button on top of the map, replacing the menu's own.
	 */
	private void createNewMenuCloseButton(Widget root)
	{
		Widget closeWidget = root.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(CLOSE_BUTTON_X, CLOSE_BUTTON_Y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.clientThread.invokeLater(() -> this.client.createScriptEventBuilder(IF_CLOSE_SCRIPT_ID)
			.build()
			.setCanSendPackets(true)
			.run()));
		closeWidget.revalidate();
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

	protected void setNewMenuWidgetsHidden(int[] childIDs, boolean hidden)
	{
		for(int childId : childIDs)
		{
			Widget widget = this.client.getWidget(MENU_NEW, childId);
			if (widget != null)
			{
				widget.setHidden(hidden);
			}
		}
	}
}
