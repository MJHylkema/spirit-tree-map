package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.TreeDefinition;
import com.mjhylkema.TeleportMaps.ui.Tree;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.Collection;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
public class SpiritTreeMap extends BaseMap implements IAdventureMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_TREES = "/SpiritTreeMap/TreeDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19000;
	private static final int MAP_SPRITE_WIDTH = 512;
	private static final int MAP_SPRITE_HEIGHT = 334;
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
	private static final String YOUR_HOUSE_STRING = "Your house";
	private static final int SPIRITUAL_FAIRY_RING_ID = 29229;

	private TreeDefinition[] treeDefinitions;
	private HashMap<String, TreeDefinition> treeDefinitionsLookup;
	private HashMap<String, Tree> availableTrees;
	private final Multimap<Integer, TreeDefinition> treeObjectIdLookup = LinkedHashMultimap.create();
	private TreeDefinition latestTree;

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
			this.treeObjectIdLookup.put(treeDefinition.getTreeObject().getObjectId(), treeDefinition);

			if (treeDefinition.getName().equals(YOUR_HOUSE_STRING))
				this.treeObjectIdLookup.put(SPIRITUAL_FAIRY_RING_ID, treeDefinition); // Spiritual Fairy ring..
		}
	}

	public void buildInterface(Widget adventureLogContainer)
	{
		this.hideAdventureLogContainerChildren(adventureLogContainer);
		this.buildAvailableTreeList();
		this.moveExitWidget();
		this.createMapWidget(adventureLogContainer);
		this.createTeleportWidgets(adventureLogContainer);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_SPIRIT_TREE_MAP:
				this.setActive(config.showSpiritTreeMap());
			default:
				super.onConfigChanged(e);
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned e)
	{
		final int gameObjectId = e.getGameObject().getId();
		if (treeObjectIdLookup.containsKey(gameObjectId))
		{
			final WorldPoint worldPoint = e.getTile().getWorldLocation();

			Collection<TreeDefinition> definitions = treeObjectIdLookup.get(gameObjectId);

			latestTree = definitions.stream().filter(def -> {
				return def.getName().equals(YOUR_HOUSE_STRING)
					|| (def.getTreeObject().getWorldPointX() == worldPoint.getX()
					&& def.getTreeObject().getWorldPointY() == worldPoint.getY());
			}).findFirst().orElse(null);

			log.debug("Latest Spirit Tree: {}", latestTree.getName());
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

	private void moveExitWidget()
	{
		Widget exitWidget = this.client.getWidget(InterfaceID.ADVENTURE_LOG, AdventureLogComposite.AdventureLog.CLOSE_BUTTON);
		if (exitWidget != null)
		{
			exitWidget.setOriginalX(428);
			exitWidget.setOriginalY(22);
			exitWidget.revalidate();
		}
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
			if (latestTree != null && latestTree == treeDefinition)
				treeTeleport.setTeleportSprites(treeDefinition.getSpriteSelected(), treeDefinition.getSpriteHover(), DISABLED_TREE_SPRITE_ID);
			else
				treeTeleport.setTeleportSprites(treeDefinition.getSpriteEnabled(), treeDefinition.getSpriteHover(), DISABLED_TREE_SPRITE_ID);

			if (isTreeUnlocked(treeDefinition.getName()))
			{
				Tree tree = this.availableTrees.get(treeDefinition.getName());

				treeTeleport.setSize(treeDefinition.getWidth(), treeDefinition.getHeight());
				treeTeleport.setName(tree.getDisplayedName());
				treeTeleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(tree));

				UIHotkey hotkey = this.createHotKey(container, treeDefinition.getHotkey(), tree.getKeyShortcut());
				treeTeleport.attachHotkey(hotkey);
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
		this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, this.client.getWidget(0xBB0003).getId(), tree.getWidget().getIndex()));
	}

	private void triggerLockedMessage(TreeDefinition treeDefinition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The Spirit Tree at %s is not available.", treeDefinition.getName()), null));
	}

	@Override
	public boolean matchesTitle(String title)
	{
		return title.equals(MENU_TITLE);
	}
}
