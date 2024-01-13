package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.MushtreeDefinition;
import com.mjhylkema.TeleportMaps.ui.Mushtree;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.config.Keybind;

public class MushtreeMap extends BaseMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_MUSHTREES = "/MushtreeMap/MushtreeDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19300;
	private static final int MAP_SPRITE_WIDTH = 467;
	private static final int MAP_SPRITE_HEIGHT = 311;
	private static final int MUSHTREE_SPRITE_ID = -19301;
	private static final int MUSHTREE_HIGHLIGHTED_SPRITE_ID = -19302;
	private static final int MUSHTREE_DISABLED_SPRITE_ID = -19303;
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGH = 23;
	private static final int CLOSE_BUTTON_X = 380;
	private static final int CLOSE_BUTTON_Y = 15;

	private static final int MUSHTREE_DIALOG_ID = 608;
	private static final String TRAVEL_ACTION = "Travel";
	private static final String MUSHTREE_LABEL_NAME_PATTERN = "<col=8f8f8f>([0-9])\\.</col> (.+)";

	private MushtreeDefinition[] mushtreeDefinitions;
	private HashMap<String, MushtreeDefinition> mushtreeDefinitionsLookup;
	private HashMap<String, Mushtree> availableMushtrees;

	public MushtreeMap(TeleportMapsPlugin plugin)
	{
		super(plugin, plugin.getConfig().showMushtreeMap());
		this.loadDefinitions();
		this.buildTreeDefinitionLookup();
	}

	private void loadDefinitions()
	{
		this.mushtreeDefinitions = this.plugin.loadDefinitionResource(MushtreeDefinition[].class, DEF_FILE_MUSHTREES);
	}

	private void buildTreeDefinitionLookup()
	{
		this.mushtreeDefinitionsLookup = new HashMap<>();
		for (MushtreeDefinition treeDefinition: this.mushtreeDefinitions)
		{
			// Place the tree definition in the lookup table indexed by its name
			this.mushtreeDefinitionsLookup.put(treeDefinition.getName(), treeDefinition);
		}
	}

	@Override
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (!this.isActive())
			return;

		if (e.getGroupId() == MUSHTREE_DIALOG_ID)
		{
			// To avoid the default mushtree dialog list flashing on the screen briefly, always hide it upfront.
			// These widgets will be un-hidden in the invokeLater if it's not the "Spirit Tree Locations".
			setWidgetsHidden(MUSHTREE_DIALOG_ID, new int[] {
				0
			}, true);

			this.plugin.getClientThread().invokeLater(() ->
			{
				Widget mushtreeDialog = this.plugin.getClient().getWidget(MUSHTREE_DIALOG_ID, 1);
				if (mushtreeDialog == null ||
					mushtreeDialog.getChild(1) == null ||
					!mushtreeDialog.getChild(1).getText().equals("Mycelium Transportation System"))
				{
					setWidgetsHidden(MUSHTREE_DIALOG_ID, new int[] {
						0
					}, false);
					return;
				}

				Widget mushtreeInterfaceContainer = this.plugin.getClient().getWidget(MUSHTREE_DIALOG_ID, 0);

				if (mushtreeInterfaceContainer == null)
					return;

				this.buildAvailableMushtreeList();
				this.hideInterfaceChildren(mushtreeInterfaceContainer);
				this.createMapWidget(mushtreeInterfaceContainer);
				this.createMushtreeWidgets(mushtreeInterfaceContainer);
				this.createEscapeButton(mushtreeInterfaceContainer);

				setWidgetsHidden(MUSHTREE_DIALOG_ID, new int[]{0}, false);
			});
		}
	}

	@Override
	public void setActive(String key, boolean active)
	{
		if (key.equals(TeleportMapsConfig.KEY_SHOW_MUSHTREE_MAP))
			this.active = active;
	}

	private void hideInterfaceChildren(Widget mushtreeInterface)
	{
		Widget[] children = mushtreeInterface.getStaticChildren();

		mushtreeInterface.setOriginalHeight(20);

		Widget mushtreeDialog = children[0]; //client.getWidget(MUSHTREE_DIALOG_ID, 1);
		mushtreeDialog.setHidden(true);

		Widget buttonParent = children[1]; //client.getWidget(MUSHTREE_DIALOG_ID, 2);
		buttonParent.setHidden(true);

		mushtreeInterface.revalidate();
	}

	/**
	 * Constructs the list of trees available for the player to use
	 */
	private void buildAvailableMushtreeList()
	{
		this.availableMushtrees = new HashMap<>();

		// Compile the pattern that will match the teleport label
		// and place the hotkey and teleport name into groups
		Pattern labelPattern = Pattern.compile(MUSHTREE_LABEL_NAME_PATTERN);

		// Get the parent widgets containing the tree list
		Widget mushtreeList = this.plugin.getClient().getWidget(MUSHTREE_DIALOG_ID, 2);

		// Fetch all tree label widgets
		Widget[] mushtreeButtons = mushtreeList.getStaticChildren();

		for (Widget mushtree : mushtreeButtons)
		{
			Widget label = mushtree.getStaticChildren()[1];

			// Create a pattern matcher with the widgets text content
			Matcher matcher = labelPattern.matcher(label.getText());

			// If the text doesn't match the pattern, skip onto the next
			if (!matcher.matches())
				continue;

			// Extract the pertinent information
			char hotKey = Character.toUpperCase(matcher.group(1).charAt(0));
			String mushtreeName = matcher.group(2);

			// Don't include unavailable mushtrees in available collection..
			if (mushtreeName.contains("Not yet found"))
				continue;

			MushtreeDefinition treeDefinition = this.mushtreeDefinitionsLookup.get(mushtreeName);

			// If a tree label by this name cannot be found in the tree definitions lookup,
			// skip. This likely means a new tree has been added to the Spirit Tree list that
			// hasn't been updated into the definitions yet
			if (treeDefinition == null)
				continue;

			this.availableMushtrees.put(mushtreeName, new Mushtree(treeDefinition, mushtree.getStaticChildren()[0], new Keybind(hotKey, 0)));
		}
	}

	private void createMapWidget(Widget mushtreeInterface)
	{
		this.createSpriteWidget(mushtreeInterface, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, 0, 0, MAP_SPRITE_ID);
	}

	private void createMushtreeWidgets(Widget mushtreeInterface)
	{
		this.clearTeleports();

		for (MushtreeDefinition mushtreeDefinition : this.mushtreeDefinitions)
		{
			Widget widgetContainer = mushtreeInterface.createChild(-1, WidgetType.GRAPHIC);
			Widget treeWidget = mushtreeInterface.createChild(-1, WidgetType.GRAPHIC);

			UITeleport mushtreeTeleport = new UITeleport(widgetContainer, treeWidget);

			mushtreeTeleport.setPosition(mushtreeDefinition.getX(), mushtreeDefinition.getY());
			mushtreeTeleport.setSize(mushtreeDefinition.getWidth(), mushtreeDefinition.getHeight());
			mushtreeTeleport.setName(mushtreeDefinition.getName());
			mushtreeTeleport.setTeleportSprites(MUSHTREE_SPRITE_ID, MUSHTREE_HIGHLIGHTED_SPRITE_ID, MUSHTREE_DISABLED_SPRITE_ID);

			if (isMushtreeAvailable(mushtreeDefinition.getName()))
			{
				Mushtree mushtree = this.availableMushtrees.get(mushtreeDefinition.getName());

				mushtreeTeleport.addAction(TRAVEL_ACTION, () -> this.triggerButton(mushtree));
				mushtreeTeleport.getWidget().setOnKeyListener((JavaScriptCallback) e ->
				{
					if (mushtree.getHotkey().getKeyCode() == e.getTypedKeyChar())
						this.triggerButton(mushtree);
				});

				UIHotkey hotkey = this.createHotKey(mushtreeInterface, mushtreeDefinition.getHotkey(), mushtree.getHotkey().toString());
				mushtreeTeleport.setHotkey(hotkey);
			}
			else
			{
				mushtreeTeleport.setLocked(true);
			}

			this.addTeleport(mushtreeTeleport);
		}
	}

	private void createEscapeButton(Widget mushtreeInterface)
	{
		Widget closeWidget = mushtreeInterface.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(CLOSE_BUTTON_X, CLOSE_BUTTON_Y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGH);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> triggerExit());
		closeWidget.revalidate();
	}

	private void triggerExit()
	{
		this.plugin.getClientThread().invoke(() -> {
			Widget mushtreeDialog = this.plugin.getClient().getWidget(MUSHTREE_DIALOG_ID, 1);
			Widget mushtreeExit = mushtreeDialog != null ? mushtreeDialog.getChild(13) : null;
			if (mushtreeExit != null)
				this.plugin.getClient().runScript(mushtreeExit.getOnOpListener());
		});
	}

	private boolean isMushtreeAvailable(String mushtreeName)
	{
		return this.availableMushtrees.containsKey(mushtreeName);
	}

	private void triggerButton(Mushtree mushtree)
	{
		this.plugin.getClientThread().invokeLater(() -> {
			Widget mushtreeWidget = mushtree.getWidget();
			Object[] listener = mushtreeWidget.getOnKeyListener();

			if (listener == null)
				return;

			listener[1] = mushtree.getHotkey().getKeyCode();
			this.plugin.getClient().runScript(listener);
		});
	}
}
