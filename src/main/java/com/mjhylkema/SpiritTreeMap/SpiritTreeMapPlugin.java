package com.mjhylkema.SpiritTreeMap;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mjhylkema.SpiritTreeMap.definition.HotKeyDefinition;
import com.mjhylkema.SpiritTreeMap.definition.TreeDefinition;
import com.mjhylkema.SpiritTreeMap.definition.SpriteDefinition;
import com.mjhylkema.SpiritTreeMap.ui.Tree;
import com.mjhylkema.SpiritTreeMap.ui.UIButton;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Spirit Tree Map"
)
public class SpiritTreeMapPlugin extends Plugin
{
	/* Definition JSON files */
	private static final String DEF_FILE_SPRITES = "/SpriteDefinitions.json";
	private static final String DEF_FILE_TREES = "/TreeDefinitions.json";

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

	private static final int HOTKEY_LABEL_SPRITE_ID = -19002;
	private static final int HOTKEY_LABEL_SPRITE_WIDTH = 20;
	private static final int HOTKEY_LABEL_SPRITE_HEIGHT = 19;

	private static final int SCRIPT_TRIGGER_KEY = 1437;
	private static final String TREE_LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";
	private static final int HOTKEY_LABEL_COLOR = 3287045; /*322805*/
	private static final int ADVENTURE_LOG_CHILD_BACKGROUND = 0;
	private static final int ADVENTURE_LOG_CHILD_TITLE = 1;
	private static final int ADVENTURE_LOG_CHILD_SCROLLBAR = 2;
	private static final int ADVENTURE_LOG_LIST = 3;
	private static final int ADVENTURE_LOG_CLOSE_BUTTON = 4;
	private static final String MENU_TITLE = "Spirit Tree Locations";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private SpiritTreeMapConfig config;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private Gson gson;

	private SpriteDefinition[] spriteDefinitions;
	private TreeDefinition[] treeDefinitions;
	private HashMap<String, TreeDefinition> treeDefinitionsLookup;
	private HashMap<String, Tree> availableTrees;
	private List<Widget> activeHotkeyLabels;

	@Override
	protected void startUp()
	{
		this.loadDefinitions();
		this.buildTreeDefinitionLookup();
		this.activeHotkeyLabels = new ArrayList<>();
		this.spriteManager.addSpriteOverrides(spriteDefinitions);
	}

	private void loadDefinitions()
	{
		this.spriteDefinitions = loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES, gson);
		this.treeDefinitions = loadDefinitionResource(TreeDefinition[].class, DEF_FILE_TREES, gson);
	}

	private <T> T loadDefinitionResource(Class<T> classType, String resource, Gson gson)
	{
		// Load the resource as a stream and wrap it in a reader
		InputStream resourceStream = classType.getResourceAsStream(resource);
		InputStreamReader definitionReader = new InputStreamReader(resourceStream);

		// Load the objects from the JSON file
		return gson.fromJson(definitionReader, classType);
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



	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == WidgetID.ADVENTURE_LOG_ID)
		{
			setAdventureLogWidgetsHidden(new int[] {
					ADVENTURE_LOG_CHILD_BACKGROUND,
					ADVENTURE_LOG_CHILD_TITLE,
					ADVENTURE_LOG_CHILD_SCROLLBAR,
					ADVENTURE_LOG_LIST,
					ADVENTURE_LOG_CLOSE_BUTTON
			}, true);

			clientThread.invokeLater(() ->
			{
				Widget spiritTreeAdventureLog = client.getWidget(WidgetInfo.ADVENTURE_LOG);

				if (spiritTreeAdventureLog == null ||
					spiritTreeAdventureLog.getChild(ADVENTURE_LOG_CHILD_TITLE) == null ||
					!spiritTreeAdventureLog.getChild(ADVENTURE_LOG_CHILD_TITLE).getText().equals(MENU_TITLE)) {

					setAdventureLogWidgetsHidden(new int[] {
							ADVENTURE_LOG_CHILD_BACKGROUND,
							ADVENTURE_LOG_CHILD_TITLE,
							ADVENTURE_LOG_CHILD_SCROLLBAR,
							ADVENTURE_LOG_LIST,
							ADVENTURE_LOG_CLOSE_BUTTON
					}, false);

					return;
				}

				setAdventureLogWidgetsHidden(new int[] {
						ADVENTURE_LOG_CHILD_BACKGROUND,
						ADVENTURE_LOG_CLOSE_BUTTON
				}, false);

				this.buildAvailableTreeList();
				this.hideSpiritTreeInterfaceWidgets(spiritTreeAdventureLog);

				this.createMapWidget(spiritTreeAdventureLog);
				this.createHouseWidget(spiritTreeAdventureLog);
				this.createTeleportWidgets(spiritTreeAdventureLog);
			});
		}
	}


	private void setAdventureLogWidgetsHidden(int[] childIds, boolean hidden) {
		for(int childId : childIds) {
			Widget widget = client.getWidget(WidgetID.ADVENTURE_LOG_ID, childId);
			if (widget != null) {
				widget.setHidden(hidden);
			}
		}
	}

	private void hideSpiritTreeInterfaceWidgets(Widget spiritTreeAdventureLog)
	{
		Widget existingBackground = spiritTreeAdventureLog.getChild(ADVENTURE_LOG_CHILD_BACKGROUND);
		existingBackground.setHidden(true);

		Widget title = spiritTreeAdventureLog.getChild(ADVENTURE_LOG_CHILD_TITLE);
		title.setHidden(true);

		Widget scrollbar = client.getWidget(WidgetID.ADVENTURE_LOG_ID, ADVENTURE_LOG_CHILD_SCROLLBAR);
		scrollbar.setHidden(true);

		Widget adventureLogTreeList = client.getWidget(WidgetID.ADVENTURE_LOG_ID, ADVENTURE_LOG_LIST);
		adventureLogTreeList.setHidden(true);
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
		Widget treeList = this.client.getWidget(WidgetID.ADVENTURE_LOG_ID, 3);

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

	private void createMapWidget(Widget adventureLog)
	{
		// Create a graphic widget for the Spirit Tree Map background
		Widget mapWidget = adventureLog.createChild(-1, WidgetType.GRAPHIC);
		mapWidget.setOriginalWidth(MAP_SPRITE_WIDTH);
		mapWidget.setOriginalHeight(MAP_SPRITE_HEIGHT);
		mapWidget.setOriginalX(0);
		mapWidget.setOriginalY(0);
		mapWidget.setSpriteId(MAP_SPRITE_ID);
		mapWidget.revalidate();

	}

	private void createHouseWidget(Widget adventureLog)
	{
		// Create a graphic widget for the Player Owned House
		Widget houseWidget = adventureLog.createChild(-1, WidgetType.GRAPHIC);
		houseWidget.setOriginalWidth(HOUSE_SPRITE_WIDTH);
		houseWidget.setOriginalHeight(HOUSE_SPRITE_HEIGHT);
		houseWidget.setOriginalX(HOUSE_WIDGET_X);
		houseWidget.setOriginalY(HOUSE_WIDGET_Y);
		houseWidget.setSpriteId(HOUSE_SPRITE_ID);
		houseWidget.revalidate();
	}

	private void createTeleportWidgets(Widget window)
	{
		this.activeHotkeyLabels.clear();

		for (TreeDefinition treeDefinition : this.treeDefinitions)
		{
			Widget treeWidget = window.createChild(-1, WidgetType.GRAPHIC);
			UIButton treeButton = new UIButton(treeWidget);
			treeButton.setPosition(treeDefinition.getX(), treeDefinition.getY());

			if (isTreeUnlocked(treeDefinition.getName()))
			{
				Tree tree = this.availableTrees.get(treeDefinition.getName());

				treeButton.setSprites(treeDefinition.getSpriteEnabled(), treeDefinition.getSpriteHover());
				treeButton.setSize(treeDefinition.getWidth(), treeDefinition.getHeight());
				treeButton.setName(tree.getDisplayedName());
				treeButton.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(tree));

				this.createHotKeyLabel(window, tree);
			}
			else
			{
				treeButton.setSprites(DISABLED_TREE_SPRITE_ID, DISABLED_TREE_SPRITE_ID);
				treeButton.setSize(DISABLED_TREE_SPRITE_WIDTH, DISABLED_TREE_SPRITE_HEIGHT);
				treeButton.setName(treeDefinition.getName());
				treeButton.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(treeDefinition));
			}

			treeWidget.revalidate();
		}
	}

	private void createHotKeyLabel(Widget window, Tree tree)
	{
		HotKeyDefinition hotkeyDefinition = tree.getDefinition().getHotkey();

		Widget hotKeyWidget = window.createChild(-1, WidgetType.GRAPHIC);
		hotKeyWidget.setOriginalWidth(HOTKEY_LABEL_SPRITE_WIDTH);
		hotKeyWidget.setOriginalHeight(HOTKEY_LABEL_SPRITE_HEIGHT);
		hotKeyWidget.setOriginalX(hotkeyDefinition.getX());
		hotKeyWidget.setOriginalY(hotkeyDefinition.getY());
		hotKeyWidget.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
		hotKeyWidget.setHidden(!config.displayHotkeys());
		this.activeHotkeyLabels.add(hotKeyWidget);

		if (config.displayHotkeys())
			hotKeyWidget.revalidate();

		Widget hotKeyText = window.createChild(-1, WidgetType.TEXT);
		hotKeyText.setText(tree.getKeyShortcut());
		hotKeyText.setFontId(FontID.QUILL_8);
		hotKeyText.setTextColor(HOTKEY_LABEL_COLOR);
		hotKeyText.setOriginalWidth(HOTKEY_LABEL_SPRITE_WIDTH);
		hotKeyText.setOriginalHeight(HOTKEY_LABEL_SPRITE_HEIGHT);
		hotKeyText.setOriginalX(hotkeyDefinition.getX() + 1);
		hotKeyText.setOriginalY(hotkeyDefinition.getY() + 1);
		hotKeyText.setXTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setYTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setHidden(!config.displayHotkeys());
		this.activeHotkeyLabels.add(hotKeyText);

		if (config.displayHotkeys())
			hotKeyText.revalidate();
	}

	private boolean isTreeUnlocked(String treeName)
	{
		return this.availableTrees.containsKey(treeName);
	}

	private void triggerTeleport(Tree tree)
	{
		this.clientThread.invokeLater(() -> client.runScript(SCRIPT_TRIGGER_KEY, client.getWidget(0xBB0003).getId(), tree.getWidget().getIndex()));
	}

	private void triggerLockedMessage(TreeDefinition treeDefinition)
	{
		this.clientThread.invokeLater(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The Spirit Tree at %s is not available.", treeDefinition.getName()), null));
	}

	@Provides
	SpiritTreeMapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SpiritTreeMapConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case SpiritTreeMapConfig.KEY_DISPLAY_HOTKEYS:
				this.clientThread.invokeLater(this::updateHotkeyLabels);
				break;
			default:
				return;
		}
	}

	private void updateHotkeyLabels()
	{
		this.activeHotkeyLabels.forEach((label) -> {
			label.setHidden(!config.displayHotkeys());
			label.revalidate();
		});
	}
}
