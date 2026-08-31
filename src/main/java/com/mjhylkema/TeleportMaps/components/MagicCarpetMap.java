package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.definition.MagicCarpetDefinition;
import com.mjhylkema.TeleportMaps.definition.TravelOptionDefinition;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.ScriptEvent;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.ImageUtil;

@Slf4j
public class MagicCarpetMap extends BaseMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_CARPETS = "/MagicCarpetMap/MagicCarpetDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19800;
	private static final int MAP_SPRITE_WIDTH = 509;
	private static final int MAP_SPRITE_HEIGHT = 317;
	private static final int CARPET_SPRITE_ID = -19801;
	private static final int CARPET_HIGHLIGHTED_SPRITE_ID = -19802;
	private static final int CARPET_SELECTED_SPRITE_ID = -19803;
	private static final int CARPET_DISABLED_SPRITE_ID = -19804;
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGHT = 23;
	private static final int CLOSE_BUTTON_X = 447;
	private static final int CLOSE_BUTTON_Y = 24;

	/* The carpet's hover, selected and disabled sprites are generated
	   from the single base image at startup */
	private static final String IMG_CARPET = "/MagicCarpetMap/Carpet.png";
	private static final Color HOVER_STROKE_INNER = new Color(238, 235, 18);
	private static final Color HOVER_STROKE_OUTER = new Color(149, 150, 44);
	private static final Color SELECTED_STROKE_INNER = new Color(106, 238, 18);
	private static final Color SELECTED_STROKE_OUTER = new Color(86, 150, 44);
	private static final float DISABLED_BRIGHTNESS = 0.55f;

	private static final int DIALOG_OPTION_GROUP_ID = 219;
	private static final int DIALOG_OPTION_CONTAINER_CHILD = 1;
	private static final String DECLINE_OPTION = "Cancel";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";

	/* The keyboard digit for the first dialog option; options natively respond
	   to the number keys matching their position in the dialog */
	private static final int OPTION_KEY_CHAR_BASE = '0';

	/* The player must be standing beside a station's rug merchant for its
	   dialog to be open. Guards against unrelated dialogs that happen to
	   share option names, and resolves stations with identical menus. */
	private static final int MAX_STATION_DISTANCE = 35;

	private MagicCarpetDefinition[] carpetDefinitions;

	/* Widgets built on the top-level interface, removed when the dialog closes */
	final private List<Widget> screenWidgets = new ArrayList<>();

	/**
	 * A dialog option present in the currently open "Select an option" menu
	 */
	private static class DialogOption
	{
		final Widget widget;
		final int childIndex;
		final String text;

		DialogOption(Widget widget, int childIndex, String text)
		{
			this.widget = widget;
			this.childIndex = childIndex;
			this.text = text;
		}
	}

	@Inject
	public MagicCarpetMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showMagicCarpetMap());
		this.loadDefinitions();
	}

	private void loadDefinitions()
	{
		this.carpetDefinitions = this.plugin.loadDefinitionResource(MagicCarpetDefinition[].class, DEF_FILE_CARPETS);
	}

	/**
	 * Registers the carpet sprites, deriving the hover, selected and
	 * disabled variants from the single base image so only one carpet
	 * asset needs to be shipped
	 */
	public void registerSprites()
	{
		BufferedImage base = ImageUtil.loadImageResource(TeleportMapsPlugin.class, IMG_CARPET);

		this.registerSprite(CARPET_SPRITE_ID, base);
		this.registerSprite(CARPET_HIGHLIGHTED_SPRITE_ID, outline(base, HOVER_STROKE_INNER, HOVER_STROKE_OUTER));
		this.registerSprite(CARPET_SELECTED_SPRITE_ID, outline(base, SELECTED_STROKE_INNER, SELECTED_STROKE_OUTER));
		this.registerSprite(CARPET_DISABLED_SPRITE_ID, grayscale(base));
	}

	private void registerSprite(int spriteId, BufferedImage image)
	{
		this.client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(image, this.client));
	}

	/**
	 * Adds a two pixel stroke around the image's shape: a bright inner
	 * ring with a darker outer ring
	 */
	private static BufferedImage outline(BufferedImage image, Color inner, Color outer)
	{
		return ImageUtil.outlineImage(ImageUtil.outlineImage(image, inner), outer);
	}

	private static BufferedImage grayscale(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int a = (argb >>> 24);
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				int grey = (int) ((0.299 * r + 0.587 * g + 0.114 * b) * DISABLED_BRIGHTNESS);
				out.setRGB(x, y, (a << 24) | (grey << 16) | (grey << 8) | grey);
			}
		}
		return out;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (!this.isActive())
			return;

		if (e.getGroupId() != DIALOG_OPTION_GROUP_ID)
			return;

		// The dialog options are populated after the interface loads,
		// so inspect the dialog on the next client cycle
		this.clientThread.invokeLater(this::tryBuildInterface);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed e)
	{
		if (e.getGroupId() == DIALOG_OPTION_GROUP_ID)
			this.destroyInterface();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_MAGIC_CARPET_MAP:
				this.setActive(config.showMagicCarpetMap());
			default:
				super.onConfigChanged(e);
		}
	}

	private void tryBuildInterface()
	{
		Widget container = this.client.getWidget(DIALOG_OPTION_GROUP_ID, DIALOG_OPTION_CONTAINER_CHILD);
		if (container == null)
			return;

		List<DialogOption> options = this.parseDialogOptions(container);
		if (options.isEmpty())
			return;

		MagicCarpetDefinition currentStation = this.identifyStation(options);
		if (currentStation == null)
			return;

		this.buildInterface(currentStation, options);
	}

	/**
	 * Collects the option entries from the chat options dialog. Only the
	 * selectable options carry key listeners; the title widget does not,
	 * so it's naturally excluded.
	 */
	private List<DialogOption> parseDialogOptions(Widget container)
	{
		List<DialogOption> options = new ArrayList<>();

		Widget[] children = container.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			String text = child.getText();

			if (text == null || text.isEmpty() || child.getOnKeyListener() == null)
				continue;

			options.add(new DialogOption(child, i, text));
		}

		return options;
	}

	/**
	 * Determines which carpet station the open dialog belongs to, if any.
	 * A station matches when every travel option in the dialog is one of the
	 * station's defined travel options. The destination names alone don't
	 * uniquely identify a station (e.g. "Pollnivneach" is offered both at
	 * Shantay Pass and at the southern terminals), and unrelated dialogs
	 * could share option names; the player must be standing beside the
	 * station's rug merchant, so the nearest in-range station with a
	 * matching menu wins.
	 * @param options the options present in the dialog
	 * @return the matching station, or null if this isn't a carpet travel dialog
	 */
	private MagicCarpetDefinition identifyStation(List<DialogOption> options)
	{
		List<DialogOption> travelChoices = new ArrayList<>();
		boolean declineFound = false;

		for (DialogOption option : options)
		{
			if (option.text.equalsIgnoreCase(DECLINE_OPTION))
				declineFound = true;
			else
				travelChoices.add(option);
		}

		// Every carpet travel menu contains a cancel option and at
		// least one destination
		if (!declineFound || travelChoices.isEmpty())
			return null;

		WorldPoint playerLocation = this.client.getLocalPlayer().getWorldLocation();
		MagicCarpetDefinition nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (MagicCarpetDefinition definition : this.carpetDefinitions)
		{
			boolean allMatch = true;
			for (DialogOption choice : travelChoices)
			{
				if (this.findTravelOption(definition, choice.text) == null)
				{
					allMatch = false;
					break;
				}
			}

			if (!allMatch)
				continue;

			int distance = Math.max(
				Math.abs(definition.getWorldPointX() - playerLocation.getX()),
				Math.abs(definition.getWorldPointY() - playerLocation.getY()));

			if (distance <= MAX_STATION_DISTANCE && distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = definition;
			}
		}

		return nearest;
	}

	private TravelOptionDefinition findTravelOption(MagicCarpetDefinition definition, String optionText)
	{
		for (TravelOptionDefinition travelOption : definition.getTravelOptions())
		{
			if (travelOption.getOption().equalsIgnoreCase(optionText))
				return travelOption;
		}
		return null;
	}

	private void buildInterface(MagicCarpetDefinition currentStation, List<DialogOption> options)
	{
		// The chat dialog is too small to hold the map (its ancestor layers
		// clip to the chatbox), so the map is built over the game view
		// instead, centered in the same modal layer the game opens
		// screen-level interfaces such as the adventure log into. The
		// scroll backdrop is part of the map sprite.
		Widget screen = this.getScreenContainer();
		if (screen == null)
			return;

		int mapX = (screen.getWidth() - MAP_SPRITE_WIDTH) / 2;
		int mapY = Math.max(0, (screen.getHeight() - MAP_SPRITE_HEIGHT) / 2);

		this.screenWidgets.clear();
		this.trackScreenWidget(
			this.createSpriteWidget(screen, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID));
		this.createCarpetWidgets(screen, currentStation, options, mapX, mapY);
		this.createCloseButton(screen, options, mapX, mapY);
	}

	/**
	 * Finds the "mainmodal" layer for the current display mode: the layer
	 * over the game view that the game opens screen-level modals into
	 */
	private Widget getScreenContainer()
	{
		int[][] containers = {
			{161, 16}, // toplevel_osrs_stretch:mainmodal (resizable classic)
			{164, 16}, // toplevel_pre_eoc:mainmodal (resizable modern)
			{548, 41}, // toplevel:mainmodal (fixed)
		};

		for (int[] componentId : containers)
		{
			Widget screen = this.client.getWidget(componentId[0], componentId[1]);
			if (screen != null)
				return screen;
		}

		return null;
	}

	private void trackScreenWidget(Widget widget)
	{
		this.screenWidgets.add(widget);
	}

	/**
	 * Variant of {@link BaseMap#createHotKey} that positions the hotkey
	 * relative to the map and tracks its widgets for later removal
	 */
	private UIHotkey createScreenHotKey(Widget screen, HotKeyDefinition hotKeyDefinition, String label, int mapX, int mapY)
	{
		Widget icon = screen.createChild(-1, WidgetType.GRAPHIC);
		icon.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
		Widget text = screen.createChild(-1, WidgetType.TEXT);
		this.trackScreenWidget(icon);
		this.trackScreenWidget(text);

		UIHotkey hotkey = new UIHotkey(icon, text);
		hotkey.setSize(hotKeyDefinition.getWidth(), hotKeyDefinition.getHeight());
		hotkey.setPosition(mapX + hotKeyDefinition.getX(), mapY + hotKeyDefinition.getY());
		hotkey.setText(label);
		hotkey.setVisibility(this.config.displayHotkeys());

		return hotkey;
	}

	/**
	 * Hides the widgets built on the top-level interface. Unlike the other
	 * maps, these aren't children of the travel dialog, so they outlive it
	 * and must be removed when the dialog closes.
	 */
	private void destroyInterface()
	{
		if (this.screenWidgets.isEmpty())
			return;

		// Snapshot the list; a new dialog may rebuild before the hide runs
		final List<Widget> widgetsToHide = new ArrayList<>(this.screenWidgets);
		this.screenWidgets.clear();
		this.clearTeleports();

		this.clientThread.invokeLater(() ->
		{
			for (Widget widget : widgetsToHide)
			{
				widget.setHidden(true);
			}
		});
	}

	/**
	 * Maps each reachable destination name to the dialog option that travels there
	 */
	private HashMap<String, DialogOption> buildDestinationLookup(MagicCarpetDefinition currentStation, List<DialogOption> options)
	{
		HashMap<String, DialogOption> destinations = new HashMap<>();

		for (DialogOption option : options)
		{
			TravelOptionDefinition travelOption = this.findTravelOption(currentStation, option.text);
			if (travelOption != null)
				destinations.put(travelOption.getDestination(), option);
		}

		return destinations;
	}

	private void createCarpetWidgets(Widget container, MagicCarpetDefinition currentStation, List<DialogOption> options, int mapX, int mapY)
	{
		this.clearTeleports();

		HashMap<String, DialogOption> destinations = this.buildDestinationLookup(currentStation, options);

		for (MagicCarpetDefinition definition : this.carpetDefinitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget carpetWidget = container.createChild(-1, WidgetType.GRAPHIC);
			this.trackScreenWidget(widgetContainer);
			this.trackScreenWidget(carpetWidget);

			UITeleport carpetTeleport = new UITeleport(widgetContainer, carpetWidget);

			carpetTeleport.setPosition(mapX + definition.getX(), mapY + definition.getY());
			carpetTeleport.setSize(MagicCarpetDefinition.getWidth(), MagicCarpetDefinition.getHeight());
			carpetTeleport.setName(definition.getName());

			DialogOption destinationOption = destinations.get(definition.getName());

			if (definition == currentStation)
			{
				// The station the player is standing at
				carpetTeleport.setTeleportSprites(CARPET_SELECTED_SPRITE_ID, CARPET_SELECTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.addAction(EXAMINE_ACTION, () -> this.triggerCurrentStationMessage(definition));
			}
			else if (destinationOption != null)
			{
				carpetTeleport.setTeleportSprites(CARPET_SPRITE_ID, CARPET_HIGHLIGHTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.addAction(TRAVEL_ACTION, () -> this.triggerTravel(destinationOption));

				// The dialog options natively respond to their number key;
				// child index lines up with the displayed option number
				int hotkeyDigit = destinationOption.childIndex;
				if (hotkeyDigit >= 1 && hotkeyDigit <= 9)
				{
					carpetTeleport.getWidget().setOnKeyListener((JavaScriptCallback) ev ->
					{
						if (ev.getTypedKeyChar() == Character.forDigit(hotkeyDigit, 10))
							this.triggerTravel(destinationOption);
					});

					UIHotkey hotkey = this.createScreenHotKey(container, definition.getHotkey(), String.valueOf(hotkeyDigit), mapX, mapY);
					carpetTeleport.attachHotkey(hotkey);
				}
			}
			else
			{
				carpetTeleport.setTeleportSprites(CARPET_SPRITE_ID, CARPET_HIGHLIGHTED_SPRITE_ID, CARPET_DISABLED_SPRITE_ID);
				carpetTeleport.setLocked(true);
				carpetTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
			}

			this.addTeleport(carpetTeleport);
		}
	}

	private void createCloseButton(Widget container, List<DialogOption> options, int mapX, int mapY)
	{
		DialogOption declineOption = null;
		for (DialogOption option : options)
		{
			if (option.text.equalsIgnoreCase(DECLINE_OPTION))
			{
				declineOption = option;
				break;
			}
		}

		if (declineOption == null)
			return;

		final DialogOption decline = declineOption;
		Widget closeWidget = container.createChild(-1, WidgetType.GRAPHIC);
		this.trackScreenWidget(closeWidget);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(mapX + CLOSE_BUTTON_X, mapY + CLOSE_BUTTON_Y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.triggerTravel(decline));
		closeWidget.revalidate();
	}

	/**
	 * Selects the given dialog option by dispatching the key listener the
	 * game attached to the option widget, with the event placeholders
	 * filled in as if the option's number key had been pressed
	 */
	private void triggerTravel(DialogOption option)
	{
		this.clientThread.invokeLater(() ->
		{
			Object[] template = option.widget.getOnKeyListener();
			if (template == null)
			{
				log.debug("No key listener on dialog option '{}'", option.text);
				return;
			}

			int digit = option.childIndex;
			if (digit < 1 || digit > 9)
				return;

			Object[] listener = new Object[template.length];
			for (int i = 0; i < template.length; i++)
			{
				Object arg = template[i];
				if (arg instanceof Integer)
				{
					switch ((Integer) arg)
					{
						case ScriptEvent.KEY_CODE:
							arg = KeyCode.KC_1 + digit - 1;
							break;
						case ScriptEvent.KEY_CHAR:
							arg = OPTION_KEY_CHAR_BASE + digit;
							break;
						case ScriptEvent.WIDGET_ID:
							arg = option.widget.getId();
							break;
						case ScriptEvent.WIDGET_INDEX:
							arg = option.widget.getIndex();
							break;
						default:
							break;
					}
				}
				listener[i] = arg;
			}

			this.client.runScript(listener);
		});
	}

	private void triggerCurrentStationMessage(MagicCarpetDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You are currently at the %s carpet station.", definition.getName()), null));
	}

	private void triggerLockedMessage(MagicCarpetDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The magic carpet cannot take you to %s from here.", definition.getName()), null));
	}
}
