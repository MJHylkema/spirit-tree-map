package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.XericsDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UILabel;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.awt.Color;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;


public class XericsMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_XERICS = "/XericsMap/XericsDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19400;
	private static final int MAP_SPRITE_WIDTH = 330;
	private static final int MAP_SPRITE_HEIGHT = 285;
	private static final int XERICS_SPRITE_ID = -19401;
	private static final int XERICS_HIGHLIGHTED_SPRITE_ID = -19402;
	private static final int XERICS_DISABLED_SPRITE_ID = -19403;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String XERICS_LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "The talisman has .*";
	private static final String MENU_TITLE_MOUNTED = "Xeric's Talisman teleports";

	private XericsDefinition[] xericsDefinitions;
	private HashMap<String, XericsDefinition> xericsDefinitionsLookup;
	private HashMap<String, AdventureLogEntry<XericsDefinition>> availableLocations;

	@Inject
	public XericsMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showXericsMap());
		this.loadDefinitions();
		this.buildXericsDefinitionLookup();
	}

	@Override
	public boolean matchesTitle(String title)
	{
		return title.matches(MENU_TITLE) || title.matches(MENU_TITLE_MOUNTED);
	}


	private void loadDefinitions()
	{
		this.xericsDefinitions = this.plugin.loadDefinitionResource(XericsDefinition[].class, DEF_FILE_XERICS);
	}

	private void buildXericsDefinitionLookup()
	{
		this.xericsDefinitionsLookup = new HashMap<>();
		for (XericsDefinition xericsDefinition: this.xericsDefinitions)
		{
			// Place the xerics definition in the lookup table indexed by its name
			this.xericsDefinitionsLookup.put(xericsDefinition.getName(), xericsDefinition);
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
			case TeleportMapsConfig.KEY_SHOW_XERICS_MAP:
				this.setActive(config.showXericsMap());
			case TeleportMapsConfig.KEY_SHOW_XERICS_MAP_LABELS:
			case TeleportMapsConfig.KEY_SHOW_XERICS_MAP_HOTKEY_LABELS:
			case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
				this.updateTeleports((teleport) -> {
					teleport.setLabelVisibility(config.showXericsMapLabels());
					teleport.setHotkeyInLabel(config.displayHotkeys() && config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels());
					teleport.setHotKeyVisibility(config.displayHotkeys() && !(config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels()));
				});
			default:
				super.onConfigChanged(e);
		}
	}

	private void hideAdventureLogContainerChildren(Widget adventureLogContainer)
	{
		Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);
	}

	/**
	 * Constructs the list of Xeric's teleports available for the player to use
	 */
	private void buildAvailableTeleportList()
	{
		this.availableLocations = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(XERICS_LABEL_NAME_PATTERN);

		// Get the parent widgets containing the teleport locations list
		Widget teleportList = this.plugin.getClient().getWidget(InterfaceID.ADVENTURE_LOG, 3);

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


			XericsDefinition xericsDefinition = this.xericsDefinitionsLookup.get(teleportName);

			if (xericsDefinition == null)
				continue;

			this.availableLocations.put(teleportName, new AdventureLogEntry<>(xericsDefinition, child, shortcutKey));
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

		for (XericsDefinition xericsDefinition : this.xericsDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget teleportWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport teleport = new UITeleport(widgetContainer, teleportWidget);

			teleport.setPosition(xericsDefinition.getX(), xericsDefinition.getY());
			teleport.setTeleportSprites(XERICS_SPRITE_ID, XERICS_HIGHLIGHTED_SPRITE_ID, XERICS_DISABLED_SPRITE_ID);
			teleport.setSize(xericsDefinition.getWidth(), xericsDefinition.getHeight());
			teleport.setName(xericsDefinition.getName());

			UILabel teleportLabel = new UILabel(container.createChild(-1, WidgetType.TEXT));
			teleportLabel.getWidget().setTextColor(Color.white.getRGB());
			teleportLabel.getWidget().setTextShadowed(true);
			teleportLabel.setText(xericsDefinition.getLabel().getTitle());
			teleportLabel.setPosition(xericsDefinition.getLabel().getX(), xericsDefinition.getLabel().getY());
			teleportLabel.setSize(xericsDefinition.getLabel().getWidth(), xericsDefinition.getLabel().getHeight());
			teleportLabel.setVisibility(config.showXericsMapLabels());
			teleport.attachLabel(teleportLabel);

			if (isLocationUnlocked(xericsDefinition.getName()))
			{
				AdventureLogEntry<XericsDefinition> adventureLogEntry = this.availableLocations.get(xericsDefinition.getName());

				teleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(adventureLogEntry));

				UIHotkey hotkey = this.createHotKey(container, xericsDefinition.getHotkey(), adventureLogEntry.getKeyShortcut());
				teleportLabel.setHotkey(adventureLogEntry.getKeyShortcut());

				teleport.attachHotkey(hotkey);

				teleport.setHotkeyInLabel(config.displayHotkeys() && config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels());
				teleport.setHotKeyVisibility(config.displayHotkeys() && !(config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels()));
			}
			else
			{
				teleport.setLocked(true);
				teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(xericsDefinition));
			}

			this.addTeleport(teleport);
		}
	}

	private boolean isLocationUnlocked(String teleportName)
	{
		return this.availableLocations.containsKey(teleportName);
	}

	private void triggerTeleport(AdventureLogEntry<XericsDefinition> adventureLogEntry)
	{
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, this.plugin.getClient().getWidget(0xBB0003).getId(), adventureLogEntry.getWidget().getIndex()));
	}

	private void triggerLockedMessage(XericsDefinition xericsDefinition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The talisman does not have the power to take you to %s yet.", xericsDefinition.getName()), null));
	}
}
