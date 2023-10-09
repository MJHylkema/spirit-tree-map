package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.definition.MushtreeDefinition;
import com.mjhylkema.TeleportMaps.ui.Mushtree;
import com.mjhylkema.TeleportMaps.ui.Tree;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.FontID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
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
	private static final int HOTKEY_LABEL_SPRITE_ID = -19304;

	private static final int MUSHTREE_DIALOG_ID = 608;
	private static final String TRAVEL_ACTION = "Travel";
	private static final String MUSHTREE_LABEL_NAME_PATTERN = "<col=8f8f8f>([0-9])\\.</col> (.+)";

	private MushtreeDefinition[] mushtreeDefinitions;
	private HashMap<String, MushtreeDefinition> mushtreeDefinitionsLookup;
	private HashMap<String, Mushtree> availableMushtrees;

	public MushtreeMap(TeleportMapsPlugin plugin)
	{
		super(plugin);
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
	public void widgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() == MUSHTREE_DIALOG_ID)
		{
			this.plugin.getClientThread().invokeLater(() ->
			{
				Widget mushtreeInterface = this.plugin.getClient().getWidget(MUSHTREE_DIALOG_ID, 0);

				if (mushtreeInterface == null)
					return;

				this.buildAvailableMushtreeList();
				this.hideInterfaceChildren(mushtreeInterface);
				this.createMapWidget(mushtreeInterface);
				this.createMushtreeWidgets(mushtreeInterface);
			});
		}
	}

	private void hideInterfaceChildren(Widget mushtreeInterface)
	{
		Widget[] children = mushtreeInterface.getStaticChildren();

		mushtreeInterface.setHeight(311); //todo see if this works using originalHeight

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
		this.clearHotKeyLabels();

		for (MushtreeDefinition mushtreeDefinition : this.mushtreeDefinitions)
		{
			Widget mushtreeWidget = mushtreeInterface.createChild(-1, WidgetType.GRAPHIC);
			UIButton mushtreeButton = new UIButton(mushtreeWidget);
			mushtreeButton.setPosition(mushtreeDefinition.getX(), mushtreeDefinition.getY());
			mushtreeButton.setSize(mushtreeDefinition.getWidth(), mushtreeDefinition.getHeight());
			mushtreeButton.setName(mushtreeDefinition.getName());

			if (isMushtreeAvailable(mushtreeDefinition.getName()))
			{
				Mushtree mushtree = this.availableMushtrees.get(mushtreeDefinition.getName());

				mushtreeButton.setSprites(MUSHTREE_SPRITE_ID, MUSHTREE_HIGHLIGHTED_SPRITE_ID);
				mushtreeButton.addAction(TRAVEL_ACTION, () -> this.triggerButton(mushtree));
				mushtreeButton.getWidget().setOnKeyListener((JavaScriptCallback) e ->
				{
					if (mushtree.getHotkey().getKeyCode() == e.getTypedKeyChar())
						this.triggerButton(mushtree);
				});

				this.createHotKeyLabel(mushtreeInterface, mushtree);
			}
			else
			{
				mushtreeButton.setSprites(MUSHTREE_DISABLED_SPRITE_ID, MUSHTREE_DISABLED_SPRITE_ID);
			}

			mushtreeWidget.revalidate();
		}
	}

	private void createHotKeyLabel(Widget container, Mushtree mushtree)
	{
		HotKeyDefinition hotkeyDefinition = mushtree.getDefinition().getHotkey();
		boolean displayHotkeys = this.plugin.getConfig().displayHotkeys();

		Widget hotKeyWidget = this.createSpriteWidget(container,
			HOTKEY_LABEL_SPRITE_WIDTH,
			HOTKEY_LABEL_SPRITE_HEIGHT,
			hotkeyDefinition.getX(),
			hotkeyDefinition.getY(),
			HOTKEY_LABEL_SPRITE_ID);
		hotKeyWidget.setHidden(!displayHotkeys);
		this.addHotkeyLabel(hotKeyWidget);

		if (displayHotkeys)
			hotKeyWidget.revalidate();

		Widget hotKeyText = container.createChild(-1, WidgetType.TEXT);
		hotKeyText.setText(mushtree.getHotkey().toString());
		hotKeyText.setFontId(FontID.QUILL_8);
		hotKeyText.setTextColor(HOTKEY_LABEL_COLOR);
		hotKeyText.setOriginalWidth(HOTKEY_LABEL_SPRITE_WIDTH);
		hotKeyText.setOriginalHeight(HOTKEY_LABEL_SPRITE_HEIGHT);
		hotKeyText.setOriginalX(hotkeyDefinition.getX() + 1);
		hotKeyText.setOriginalY(hotkeyDefinition.getY() + 1);
		hotKeyText.setXTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setYTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setHidden(!displayHotkeys);
		this.addHotkeyLabel(hotKeyText);

		if (displayHotkeys)
			hotKeyText.revalidate();
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

	@Override
	public void changeLabelVisibility()
	{

	}
}
