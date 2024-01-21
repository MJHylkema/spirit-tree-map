package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.ObeliskDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UILabel;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.awt.Color;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
public class WildernessObeliskMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_OBELISKS = "/WildernessObeliskMap/ObeliskDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19600;
	private static final int MAP_SPRITE_WIDTH = 474;
	private static final int MAP_SPRITE_HEIGHT = 295;
	private static final int OBELISK_SPRITE_DISABLED = -19601;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String SET_ACTION = "Set";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "Select Obelisk destination";
	private static final int POH_OBELISK_ID = 31554;

	private ObeliskDefinition[] obeliskDefinitions;
	private HashMap<String, ObeliskDefinition> obeliskDefinitionLookup;
	private HashMap<String, AdventureLogEntry<ObeliskDefinition>> availableLocations;
	private HashMap<Integer, ObeliskDefinition> obeliskGameObjectLookup;
	private ObeliskDefinition latestObelisk;

	@Inject
	public WildernessObeliskMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showObeliskMap());
		this.loadDefinitions();
		this.buildObeliskDefinitionLookup();
	}

	@Override
	public boolean matchesTitle(String title)
	{
		return title.matches(MENU_TITLE);
	}

	private void loadDefinitions()
	{
		this.obeliskDefinitions = this.plugin.loadDefinitionResource(ObeliskDefinition[].class, DEF_FILE_OBELISKS);
	}

	private void buildObeliskDefinitionLookup()
	{
		this.obeliskDefinitionLookup = new HashMap<>();
		this.obeliskGameObjectLookup = new HashMap<>();
		for (ObeliskDefinition obeliskDefinition: this.obeliskDefinitions)
		{
			// Place the obelisk definition in the lookup table indexed by its name
			this.obeliskDefinitionLookup.put(obeliskDefinition.getName(), obeliskDefinition);
			this.obeliskGameObjectLookup.put(obeliskDefinition.getObeliskObjectId(), obeliskDefinition);
		}

		this.obeliskGameObjectLookup.put(POH_OBELISK_ID, null);
	}

	@Override
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
			case TeleportMapsConfig.KEY_SHOW_OBELISK_MAP:
				this.setActive(config.showObeliskMap());
			case TeleportMapsConfig.KEY_SHOW_OBELISK_MAP_LABELS:
			case TeleportMapsConfig.KEY_SHOW_OBELISK_MAP_HOTKEY_LABELS:
			case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
				this.updateTeleports((teleport) -> {
					teleport.setLabelVisibility(config.showObeliskMapLabels());
					teleport.setHotkeyInLabel(config.displayHotkeys() && config.showObeliskMapLabels() && config.showObeliskMapHotkeyInLabels());
					teleport.setHotKeyVisibility(config.displayHotkeys() && !(config.showObeliskMapLabels() && config.showObeliskMapHotkeyInLabels()));
				});
			default:
				super.onConfigChanged(e);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		final int gameObjectId = e.getGameObject().getId();
		if (obeliskGameObjectLookup.containsKey(gameObjectId))
		{
			latestObelisk = obeliskGameObjectLookup.get(gameObjectId);
		}
	}

	private void hideAdventureLogContainerChildren(Widget adventureLogContainer)
	{
		Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);
	}

	/**
	 * Constructs the list of Obelisk teleports available for the player to use
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

			ObeliskDefinition obeliskDefinition = this.obeliskDefinitionLookup.get(teleportName);

			if (obeliskDefinition == null)
				continue;

			this.availableLocations.put(teleportName, new AdventureLogEntry<>(obeliskDefinition, child, shortcutKey));
		}
	}

	private void createMapWidget(Widget container)
	{
		this.createSpriteWidget(container,
			MAP_SPRITE_WIDTH,
			MAP_SPRITE_HEIGHT,
			19,
			33,
			MAP_SPRITE_ID);
	}

	private void createTeleportWidgets(Widget container)
	{
		this.clearTeleports();

		for (ObeliskDefinition obeliskDefinition : this.obeliskDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget teleportWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport teleport = new UITeleport(widgetContainer, teleportWidget);

			if (latestObelisk != null && latestObelisk == obeliskDefinition)
				teleport.setTeleportSprites(obeliskDefinition.getSpriteSelected(), obeliskDefinition.getSpriteHover(), OBELISK_SPRITE_DISABLED);
			else
				teleport.setTeleportSprites(obeliskDefinition.getSpriteEnabled(), obeliskDefinition.getSpriteHover(), OBELISK_SPRITE_DISABLED);

			teleport.setPosition(obeliskDefinition.getX(), obeliskDefinition.getY());
			teleport.setSize(obeliskDefinition.getWidth(), obeliskDefinition.getHeight());
			teleport.setName(obeliskDefinition.getName());

			UILabel teleportLabel = new UILabel(container.createChild(-1, WidgetType.TEXT));
			teleportLabel.getWidget().setTextColor(Color.white.getRGB());
			teleportLabel.getWidget().setTextShadowed(true);
			teleportLabel.setText(obeliskDefinition.getLabel().getTitle());
			teleportLabel.setPosition(obeliskDefinition.getLabel().getX(), obeliskDefinition.getLabel().getY());
			teleportLabel.setSize(obeliskDefinition.getLabel().getWidth(), obeliskDefinition.getLabel().getHeight());
			teleportLabel.setVisibility(config.showObeliskMapLabels());
			teleport.attachLabel(teleportLabel);

			if (isLocationUnlocked(obeliskDefinition.getName()))
			{
				AdventureLogEntry<ObeliskDefinition> adventureLogEntry = this.availableLocations.get(obeliskDefinition.getName());

				teleport.addAction(SET_ACTION, () -> this.triggerTeleport(adventureLogEntry));

				UIHotkey hotkey = this.createHotKey(container, obeliskDefinition.getHotkey(), adventureLogEntry.getKeyShortcut());
				teleportLabel.setHotkey(adventureLogEntry.getKeyShortcut());
				teleport.attachHotkey(hotkey);

				teleport.setHotkeyInLabel(config.displayHotkeys() && config.showObeliskMapLabels() && config.showObeliskMapHotkeyInLabels());
				teleport.setHotKeyVisibility(config.displayHotkeys() && !(config.showObeliskMapLabels() && config.showObeliskMapHotkeyInLabels()));
			}
			else
			{
				teleport.setLocked(true);
				teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(obeliskDefinition));
			}

			this.addTeleport(teleport);
		}
	}

	private boolean isLocationUnlocked(String teleportName)
	{
		return this.availableLocations.containsKey(teleportName);
	}

	private void triggerTeleport(AdventureLogEntry<ObeliskDefinition> adventureLogEntry)
	{
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, this.client.getWidget(0xBB0003).getId(), adventureLogEntry.getWidget().getIndex()));
	}

	private void triggerLockedMessage(ObeliskDefinition obeliskDefinition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You are unable to travel to %s.", obeliskDefinition.getName()), null));
	}
}
