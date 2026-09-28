package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.XericsDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UILabel;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import com.mjhylkema.TeleportMaps.ui.Xerics;
import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
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
	private static final int MAP_SPRITE_WIDTH = 509;
	private static final int MAP_SPRITE_HEIGHT = 317;
	private static final int XERICS_SPRITE_ID = -19401;
	private static final int XERICS_HIGHLIGHTED_SPRITE_ID = -19402;
	private static final int XERICS_DISABLED_SPRITE_ID = -19403;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String XERICS_LABEL_NAME_PATTERN = AdventureLogComposite.ENTRY_LABEL_PATTERN;
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "The talisman has .*";
	private static final String MENU_TITLE_MOUNTED = "Xeric's Talisman teleports";
	private static final int MIN_MATCHING_ENTRIES = 2;

	private XericsDefinition[] xericsDefinitions;
	private HashMap<Integer, XericsDefinition> xericsDefinitionsLookup;
	private HashMap<String, XericsDefinition> xericsDefinitionsNameLookup;
	private HashMap<String, Xerics> availableLocations;

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

	@Override
	public boolean matchesEntries(List<String> entryNames)
	{
		int matches = 0;
		for (String entryName : entryNames)
		{
			if (this.xericsDefinitionsNameLookup.containsKey(entryName))
				matches++;
		}
		return matches >= MIN_MATCHING_ENTRIES;
	}

	private void loadDefinitions()
	{
		this.xericsDefinitions = this.plugin.loadDefinitionResource(XericsDefinition[].class, DEF_FILE_XERICS);
	}

	private void buildXericsDefinitionLookup()
	{
		this.xericsDefinitionsLookup = new HashMap<>();
		this.xericsDefinitionsNameLookup = new HashMap<>();
		for (XericsDefinition xericsDefinition: this.xericsDefinitions)
		{
			// Place the xerics definition in the lookup tables indexed by its entry index and name
			this.xericsDefinitionsLookup.put(xericsDefinition.getIndex(), xericsDefinition);
			this.xericsDefinitionsNameLookup.put(xericsDefinition.getName(), xericsDefinition);
		}
	}

	public void buildInterface(Widget adventureLogContainer, Widget entryList)
	{
		this.hideAdventureLogContainerChildren(adventureLogContainer);
		this.buildAvailableTeleportList(entryList);

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
		Widget existingBackground = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_BACKGROUND);
		if (existingBackground != null)
			existingBackground.setHidden(true);

		Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
		if (title != null)
			title.setHidden(true);
	}

	/**
	 * Constructs the list of Xeric's teleports available for the player to use
	 */
	private void buildAvailableTeleportList(Widget teleportList)
	{
		this.availableLocations = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(XERICS_LABEL_NAME_PATTERN);

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

			// Match by name first, as the mounted talisman's entries may not share
			// the talisman's order. Fall back to the entry's index for plugins
			// such as "Easy Teleports" that rename the entries.
			XericsDefinition xericsDefinition = this.xericsDefinitionsNameLookup.get(teleportName);
			if (xericsDefinition == null)
				xericsDefinition = this.xericsDefinitionsLookup.get(child.getIndex());

			if (xericsDefinition == null)
				continue;

			this.availableLocations.put(xericsDefinition.getName(), new Xerics(xericsDefinition, child, shortcutKey, teleportName));
		}
	}

	private void createMapWidget(Widget container)
	{
		this.createSpriteWidget(container,
			MAP_SPRITE_WIDTH,
			MAP_SPRITE_HEIGHT,
			2,
			13,
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
				Xerics xericsEntry = this.availableLocations.get(xericsDefinition.getName());

				teleport.setName(xericsEntry.getDisplayedName());
				teleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(xericsEntry));

				UIHotkey hotkey = this.createHotKey(container, xericsDefinition.getHotkey(), xericsEntry.getKeyShortcut());
				teleportLabel.setHotkey(xericsEntry.getKeyShortcut());

				// If the button displayed name differs from the original,
				// use this for the label instead of our default label name
				if (!xericsEntry.getDisplayedName().equals(xericsDefinition.getName()))
					teleportLabel.setText(xericsEntry.getDisplayedName());

				teleport.attachHotkey(hotkey);

				teleport.setHotkeyInLabel(config.displayHotkeys() && config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels());
				teleport.setHotKeyVisibility(config.displayHotkeys() && !(config.showXericsMapLabels() && config.showXericsMapHotkeyInLabels()));
			}
			else
			{
				teleport.setName(xericsDefinition.getName());
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
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, adventureLogEntry.getWidget().getId(), adventureLogEntry.getWidget().getIndex()));
	}

	private void triggerLockedMessage(XericsDefinition xericsDefinition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The talisman does not have the power to take you to %s yet.", xericsDefinition.getName()), null));
	}
}
