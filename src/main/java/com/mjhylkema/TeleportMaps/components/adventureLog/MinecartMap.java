package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.MinecartDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
public class MinecartMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_MINECART = "/MinecartMap/MinecartDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19400;
	private static final int MAP_SPRITE_WIDTH = 330;
	private static final int MAP_SPRITE_HEIGHT = 285;
	private static final int MINECART_SPRITE_ID = -19501;
	private static final int MINECART_HIGHLIGHTED_SPRITE_ID = -19502;
	private static final int MINECART_DISABLED_SPRITE_ID = -19503;
	private static final int MINECART_SELECTED_SPRITE_ID = -19504;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "Minecart rides: .*";

	private MinecartDefinition[] minecartDefinitions;
	private HashMap<String, MinecartDefinition> minecartDefinitionLookup;
	private HashMap<String, AdventureLogEntry<MinecartDefinition>> availableLocations;
	private HashSet<Integer> minecartNpcLookup;
	private int latestNpc;

	@Inject
	public MinecartMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showMinecartMap());
		this.loadDefinitions();
		this.buildMinecartDefinitionLookup();
	}

	@Override
	public boolean matchesTitle(String title)
	{
		return title.matches(MENU_TITLE);
	}


	private void loadDefinitions()
	{
		this.minecartDefinitions = this.plugin.loadDefinitionResource(MinecartDefinition[].class, DEF_FILE_MINECART);
	}

	private void buildMinecartDefinitionLookup()
	{
		this.minecartDefinitionLookup = new HashMap<>();
		this.minecartNpcLookup = new HashSet<>();
		for (MinecartDefinition minecartDefinition: this.minecartDefinitions)
		{
			// Place the minecart definition in the lookup table indexed by its name
			this.minecartDefinitionLookup.put(minecartDefinition.getName(), minecartDefinition);
			this.minecartNpcLookup.add(minecartDefinition.getNpcId());
		}
	}

	public void buildInterface(Widget adventureLogContainer)
	{
		this.hideAdventureLogContainerChildren(adventureLogContainer);
		this.buildAvailableTeleportList();

		this.createMapWidget(adventureLogContainer);
		this.createTeleportWidgets(adventureLogContainer);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_MINECART_MAP:
				this.setActive(config.showMinecartMap());
			default:
				super.onConfigChanged(e);
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned e)
	{
		if (this.minecartNpcLookup.contains(e.getNpc().getId()))
		{
			latestNpc = e.getNpc().getId();

			log.debug("Latest Minecart NPC: {}", e.getNpc().getName());
		}
	}

	private void hideAdventureLogContainerChildren(Widget adventureLogContainer)
	{
		Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);
	}

	/**
	 * Constructs the list of Minecart teleports available for the player to use
	 */
	private void buildAvailableTeleportList()
	{
		this.availableLocations = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(LABEL_NAME_PATTERN);

		// Get the parent widgets containing the teleport locations list
		Widget teleportList = this.client.getWidget(InterfaceID.ADVENTURE_LOG, 3);

		// Fetch all teleport label widgets
		Widget[] labelWidgets = teleportList.getDynamicChildren();

		for (Widget child : labelWidgets)
		{
			String shortcutKey;
			String disabledColor;
			String teleportName;

			// Create a pattern matcher with the widgets text content
			Matcher matcher = labelPattern.matcher(child.getText());

			// If the text doesn't match the pattern, skip onto the next
			if (!matcher.matches())
				continue;

			// Extract the pertinent information
			shortcutKey = matcher.group(1);
			disabledColor = matcher.group(2);
			teleportName = matcher.group(3);

			// Don't include unavailable teleports in available collection..
			if (disabledColor != null)
				continue;


			MinecartDefinition minecartDefinition = this.minecartDefinitionLookup.get(teleportName);

			if (minecartDefinition == null)
				continue;

			this.availableLocations.put(teleportName, new AdventureLogEntry<>(minecartDefinition, child, shortcutKey));
		}
	}

	private void createMapWidget(Widget container)
	{
		this.createSpriteWidget(container,
			MAP_SPRITE_WIDTH,
			MAP_SPRITE_HEIGHT,
			66,
			43,
			MAP_SPRITE_ID);
	}

	private void createTeleportWidgets(Widget container)
	{
		this.clearTeleports();

		for (MinecartDefinition minecartDefinition : this.minecartDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget teleportWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport teleport = new UITeleport(widgetContainer, teleportWidget);

			if (this.latestNpc != 0 && this.latestNpc == minecartDefinition.getNpcId())
				teleport.setTeleportSprites(MINECART_SELECTED_SPRITE_ID, MINECART_HIGHLIGHTED_SPRITE_ID, MINECART_DISABLED_SPRITE_ID);
			else
				teleport.setTeleportSprites(MINECART_SPRITE_ID, MINECART_HIGHLIGHTED_SPRITE_ID, MINECART_DISABLED_SPRITE_ID);

			teleport.setPosition(minecartDefinition.getX(), minecartDefinition.getY());
			teleport.setSize(minecartDefinition.getWidth(), minecartDefinition.getHeight());
			teleport.setName(minecartDefinition.getName());

			if (isLocationUnlocked(minecartDefinition.getName()))
			{
				AdventureLogEntry<MinecartDefinition> adventureLogEntry = this.availableLocations.get(minecartDefinition.getName());

				teleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(adventureLogEntry));

				UIHotkey hotkey = this.createHotKey(container, minecartDefinition.getHotkey(), adventureLogEntry.getKeyShortcut());
				teleport.attachHotkey(hotkey);
			}
			else
			{
				teleport.setLocked(true);
				teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(minecartDefinition));
			}

			this.addTeleport(teleport);
		}
	}

	private boolean isLocationUnlocked(String teleportName)
	{
		return this.availableLocations.containsKey(teleportName);
	}

	private void triggerTeleport(AdventureLogEntry<MinecartDefinition> adventureLogEntry)
	{
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, this.client.getWidget(0xBB0003).getId(), adventureLogEntry.getWidget().getIndex()));
	}

	private void triggerLockedMessage(MinecartDefinition minecartDefinition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You are unable to travel to %s.", minecartDefinition.getName()), null));
	}
}
