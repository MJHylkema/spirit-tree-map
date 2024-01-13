package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.XericsDefinition;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UILabel;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import com.mjhylkema.TeleportMaps.ui.Xerics;
import java.awt.Color;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.ChatMessageType;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetType;


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
	private static final String XERICS_LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "The talisman has .*";

	@Override
	public boolean isActiveWidget(String title)
	{
		return this.isActive() && title.matches(MENU_TITLE);
	}

	private XericsDefinition[] xericsDefinitions;
	private HashMap<String, XericsDefinition> xericsDefinitionsLookup;
	private HashMap<String, Xerics> availableTrees;

	public XericsMap(TeleportMapsPlugin plugin)
	{
		super(plugin, plugin.getConfig().showXericsMap());
		this.loadDefinitions();
		this.buildTreeDefinitionLookup();
	}

	private void loadDefinitions()
	{
		this.xericsDefinitions = this.plugin.loadDefinitionResource(XericsDefinition[].class, DEF_FILE_XERICS);
	}

	private void buildTreeDefinitionLookup()
	{
		this.xericsDefinitionsLookup = new HashMap<>();
		for (XericsDefinition xericsDefinition: this.xericsDefinitions)
		{
			// Place the tree definition in the lookup table indexed by its name
			this.xericsDefinitionsLookup.put(xericsDefinition.getName(), xericsDefinition);
		}
	}

	@Override
	public void onWidgetLoaded(WidgetLoaded e)
	{
		Widget adventureLogContainer = this.plugin.getClient().getWidget(WidgetInfo.ADVENTURE_LOG);

		this.hideAdventureLogContainerChildren(adventureLogContainer);
		this.buildAvailableTreeList();

		this.createMapWidget(adventureLogContainer);
		this.createTeleportWidgets(adventureLogContainer);
	}

	@Override
	public void setActive(String key, boolean active)
	{
		if (key.equals(TeleportMapsConfig.KEY_SHOW_XERICS_MAP))
			this.active = active;
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
	 * Constructs the list of trees available for the player to use
	 */
	private void buildAvailableTreeList()
	{
		this.availableTrees = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(XERICS_LABEL_NAME_PATTERN);

		// Get the parent widgets containing the tree list
		Widget treeList = this.plugin.getClient().getWidget(WidgetID.ADVENTURE_LOG_ID, 3);

		// Fetch all tree label widgets
		Widget[] labelWidgets = treeList.getDynamicChildren();

		for (Widget child : labelWidgets)
		{
			String shortcutKey;
			String disabledColor;
			String treeName;

			String displayedName;

			// Create a pattern matcher with the widgets text content
			Matcher matcher = labelPattern.matcher(child.getText());

			// If the text doesn't match the pattern, skip onto the next
			if (!matcher.matches())
				continue;

			// Extract the pertinent information
			shortcutKey = matcher.group(1);
			disabledColor = matcher.group(2);
			treeName = matcher.group(3);

			// Don't include unavailable trees in available collection..
			if (disabledColor != null)
				continue;


			XericsDefinition xericsDefinition = this.xericsDefinitionsLookup.get(treeName);

			// If a tree label by this name cannot be found in the tree definitions lookup,
			// skip. This likely means a new tree has been added to the Spirit Tree list that
			// hasn't been updated into the definitions yet
			if (xericsDefinition == null)
				continue;

			this.availableTrees.put(treeName, new Xerics(xericsDefinition, child, shortcutKey));
		}
	}

	private void createMapWidget(Widget container)
	{
		this.createSpriteWidget(container,
			MAP_SPRITE_WIDTH,
			MAP_SPRITE_HEIGHT,
			0,
			0,
			MAP_SPRITE_ID);
	}

	private void createTeleportWidgets(Widget container)
	{
		this.clearTeleports();

		for (XericsDefinition treeDefinition : this.xericsDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget treeWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport treeTeleport = new UITeleport(widgetContainer, treeWidget);

			treeTeleport.setPosition(treeDefinition.getX(), treeDefinition.getY());
			treeTeleport.setTeleportSprites(XERICS_SPRITE_ID, XERICS_HIGHLIGHTED_SPRITE_ID, XERICS_DISABLED_SPRITE_ID);
			treeTeleport.setSize(treeDefinition.getWidth(), treeDefinition.getHeight());
			treeTeleport.setName(treeDefinition.getName());

			if (isTreeUnlocked(treeDefinition.getName()))
			{
				Xerics tree = this.availableTrees.get(treeDefinition.getName());

				treeTeleport.setName(treeDefinition.getName());
				treeTeleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(tree));

				UIHotkey hotkey = this.createHotKey(container, treeDefinition.getHotkey(), tree.getKeyShortcut());
				treeTeleport.setHotkey(hotkey);
			}
			else
			{
				treeTeleport.setLocked(true);
				treeTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(treeDefinition));
			}


			Widget labelWidget = container.createChild(-1, WidgetType.TEXT);
			UILabel xericsLabel = new UILabel(labelWidget);
			labelWidget.setTextColor(Color.white.getRGB());
			labelWidget.setTextShadowed(true);
			xericsLabel.setText(treeDefinition.getLabel().getTitle());
			xericsLabel.setPosition(treeDefinition.getLabel().getX(), treeDefinition.getLabel().getY());
			xericsLabel.setSize(treeDefinition.getLabel().getWidth(), treeDefinition.getLabel().getHeight());

			this.addTeleport(treeTeleport);
		}
	}

	private boolean isTreeUnlocked(String treeName)
	{
		return this.availableTrees.containsKey(treeName);
	}

	private void triggerTeleport(Xerics tree)
	{
		this.plugin.getClientThread().invokeLater(() -> this.plugin.getClient().runScript(SCRIPT_TRIGGER_KEY, this.plugin.getClient().getWidget(0xBB0003).getId(), tree.getWidget().getIndex()));
	}

	private void triggerLockedMessage(XericsDefinition treeDefinition)
	{
		this.plugin.getClientThread().invokeLater(() -> this.plugin.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The talisman does not have the power to take you to %s yet.", treeDefinition.getName()), null));
	}
}
