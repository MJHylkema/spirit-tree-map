package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.TreeDefinition;
import com.mjhylkema.TeleportMaps.ui.Tree;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.HashMap;
import java.util.Objects;
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

public class SpiritTreeMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_TREES = "/SpiritTreeMap/TreeDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19000;
	private static final int MAP_SPRITE_WIDTH = 508;
	private static final int MAP_SPRITE_HEIGHT = 319;
	private static final int HOUSE_SPRITE_ID = -19001;
	private static final int HOUSE_SPRITE_WIDTH = 53;
	private static final int HOUSE_SPRITE_HEIGHT = 49;
	private static final int HOUSE_WIDGET_X = 36;
	private static final int HOUSE_WIDGET_Y = 241;
	private static final int DISABLED_TREE_SPRITE_ID = -19102;
	private static final int DISABLED_TREE_SPRITE_WIDTH = 19;
	private static final int DISABLED_TREE_SPRITE_HEIGHT = 27;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String TREE_LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;
	private static final String MENU_TITLE = "Spirit Tree Locations";

	private TreeDefinition[] treeDefinitions;
	private HashMap<String, TreeDefinition> treeDefinitionsLookup;
	private HashMap<String, Tree> availableTrees;

	@Inject
	public SpiritTreeMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showSpiritTreeMap());
		this.loadDefinitions();
		this.buildTreeDefinitionLookup();
	}

	private void loadDefinitions()
	{
		this.treeDefinitions = this.plugin.loadDefinitionResource(TreeDefinition[].class, DEF_FILE_TREES);
	}

	private void buildTreeDefinitionLookup()
	{
		this.treeDefinitionsLookup = new HashMap<>();
		for (TreeDefinition treeDefinition: this.treeDefinitions)
		{
			// Place the tree definition in the lookup table indexed by its name
			this.treeDefinitionsLookup.put(treeDefinition.getName(), treeDefinition);
		}
	}

	public void buildInterface(Widget adventureLogContainer)
	{
		this.hideAdventureLogContainerChildren(adventureLogContainer);
		this.buildAvailableTreeList();

		this.createMapWidget(adventureLogContainer);
		this.createHouseWidget(adventureLogContainer);
		this.createTeleportWidgets(adventureLogContainer);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (Objects.equals(e.getKey(), TeleportMapsConfig.KEY_SHOW_SPIRIT_TREE_MAP))
			this.setActive(config.showSpiritTreeMap());
		else
			super.onConfigChanged(e);
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
		Pattern labelPattern = Pattern.compile(TREE_LABEL_NAME_PATTERN);

		// Get the parent widgets containing the tree list
		Widget treeList = this.plugin.getClient().getWidget(InterfaceID.ADVENTURE_LOG, 3);

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

			// Your house may include a bracketed location afterwards.
			// Use this full name for the button, but don't use it for the lookup key.
			if (treeName.contains("Your house"))
			{
				displayedName = treeName;
				treeName = "Your house";
			}
			else
			{
				displayedName = treeName;
			}

			TreeDefinition treeDefinition = this.treeDefinitionsLookup.get(treeName);

			// If a tree label by this name cannot be found in the tree definitions lookup,
			// skip. This likely means a new tree has been added to the Spirit Tree list that
			// hasn't been updated into the definitions yet
			if (treeDefinition == null)
				continue;

			this.availableTrees.put(treeName, new Tree(treeDefinition, child, shortcutKey, displayedName));
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

	private void createHouseWidget(Widget container)
	{
		this.createSpriteWidget(container,
			HOUSE_SPRITE_WIDTH,
			HOUSE_SPRITE_HEIGHT,
			HOUSE_WIDGET_X,
			HOUSE_WIDGET_Y,
			HOUSE_SPRITE_ID);
	}

	private void createTeleportWidgets(Widget container)
	{
		this.clearTeleports();

		for (TreeDefinition treeDefinition : this.treeDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget treeWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport treeTeleport = new UITeleport(widgetContainer, treeWidget);

			treeTeleport.setPosition(treeDefinition.getX(), treeDefinition.getY());
			treeTeleport.setTeleportSprites(treeDefinition.getSpriteEnabled(), treeDefinition.getSpriteHover(), DISABLED_TREE_SPRITE_ID);

			if (isTreeUnlocked(treeDefinition.getName()))
			{
				Tree tree = this.availableTrees.get(treeDefinition.getName());

				treeTeleport.setSize(treeDefinition.getWidth(), treeDefinition.getHeight());
				treeTeleport.setName(tree.getDisplayedName());
				treeTeleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(tree));

				UIHotkey hotkey = this.createHotKey(container, treeDefinition.getHotkey(), tree.getKeyShortcut());
				treeTeleport.setHotkey(hotkey);
			}
			else
			{
				treeTeleport.setLocked(true);
				treeTeleport.setSize(DISABLED_TREE_SPRITE_WIDTH, DISABLED_TREE_SPRITE_HEIGHT);
				treeTeleport.setName(treeDefinition.getName());
				treeTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(treeDefinition));
			}

			this.addTeleport(treeTeleport);
		}
	}

	private boolean isTreeUnlocked(String treeName)
	{
		return this.availableTrees.containsKey(treeName);
	}

	private void triggerTeleport(Tree tree)
	{
		this.plugin.getClientThread().invokeLater(() -> this.plugin.getClient().runScript(SCRIPT_TRIGGER_KEY, this.plugin.getClient().getWidget(0xBB0003).getId(), tree.getWidget().getIndex()));
	}

	private void triggerLockedMessage(TreeDefinition treeDefinition)
	{
		this.plugin.getClientThread().invokeLater(() -> this.plugin.getClient().addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The Spirit Tree at %s is not available.", treeDefinition.getName()), null));
	}

	@Override
	public boolean matchesTitle(String title)
	{
		return title.equals(MENU_TITLE);
	}
}
