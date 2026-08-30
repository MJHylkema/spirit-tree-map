package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.MagicCarpetDefinition;
import com.mjhylkema.TeleportMaps.definition.TravelOptionDefinition;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

@Slf4j
public class MagicCarpetMap extends BaseMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_CARPETS = "/MagicCarpetMap/MagicCarpetDefinitions.json";

	/* Sprite IDs, dimensions and positions */
	private static final int MAP_SPRITE_ID = -19800;
	private static final int MAP_SPRITE_WIDTH = 202;
	private static final int MAP_SPRITE_HEIGHT = 335;
	private static final int CARPET_SPRITE_ID = -19801;
	private static final int CARPET_HIGHLIGHTED_SPRITE_ID = -19802;
	private static final int CARPET_SELECTED_SPRITE_ID = -19803;
	private static final int CARPET_DISABLED_SPRITE_ID = -19804;
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGHT = 23;

	private static final int DIALOG_OPTION_GROUP_ID = 219;
	private static final int DIALOG_OPTION_CONTAINER_CHILD = 1;
	private static final String DIALOG_TITLE = "Select an option";
	private static final String DECLINE_OPTION_PREFIX = "I don't want to travel";
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";

	private MagicCarpetDefinition[] carpetDefinitions;

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

		this.buildInterface(container, currentStation, options);
	}

	/**
	 * Collects the option entries from the "Select an option" dialog,
	 * excluding the title
	 */
	private List<DialogOption> parseDialogOptions(Widget container)
	{
		List<DialogOption> options = new ArrayList<>();

		Widget[] children = container.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			String text = child.getText();

			if (text == null || text.isEmpty() || text.equals(DIALOG_TITLE))
				continue;

			options.add(new DialogOption(child, i, text));
		}

		return options;
	}

	/**
	 * Determines which carpet station the open dialog belongs to, if any.
	 * A station matches when every travel option in the dialog is one of the
	 * station's defined travel options. Some stations share identical menus
	 * (e.g. Nardah and Menaphos), so ties are broken by the player's distance
	 * to the station.
	 * @param options the options present in the dialog
	 * @return the matching station, or null if this isn't a carpet travel dialog
	 */
	private MagicCarpetDefinition identifyStation(List<DialogOption> options)
	{
		List<DialogOption> travelChoices = new ArrayList<>();
		boolean declineFound = false;

		for (DialogOption option : options)
		{
			if (option.text.startsWith(DECLINE_OPTION_PREFIX))
				declineFound = true;
			else
				travelChoices.add(option);
		}

		// Every carpet travel menu contains a decline option and at
		// least one destination
		if (!declineFound || travelChoices.isEmpty())
			return null;

		List<MagicCarpetDefinition> candidates = new ArrayList<>();
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

			if (allMatch)
				candidates.add(definition);
		}

		if (candidates.isEmpty())
			return null;

		if (candidates.size() == 1)
			return candidates.get(0);

		// Multiple stations share this menu; the player is standing
		// beside the station, so pick the nearest one
		WorldPoint playerLocation = this.client.getLocalPlayer().getWorldLocation();
		MagicCarpetDefinition nearest = null;
		int nearestDistance = Integer.MAX_VALUE;

		for (MagicCarpetDefinition candidate : candidates)
		{
			int dx = candidate.getWorldPointX() - playerLocation.getX();
			int dy = candidate.getWorldPointY() - playerLocation.getY();
			int distance = dx * dx + dy * dy;

			if (distance < nearestDistance)
			{
				nearestDistance = distance;
				nearest = candidate;
			}
		}

		return nearest;
	}

	private TravelOptionDefinition findTravelOption(MagicCarpetDefinition definition, String optionText)
	{
		for (TravelOptionDefinition travelOption : definition.getTravelOptions())
		{
			if (travelOption.getOption().equals(optionText))
				return travelOption;
		}
		return null;
	}

	private void buildInterface(Widget container, MagicCarpetDefinition currentStation, List<DialogOption> options)
	{
		// Hide the original dialog title and options
		for (Widget child : container.getDynamicChildren())
		{
			child.setHidden(true);
		}

		// Anchor the map to the bottom of the dialog container, centered
		// horizontally. The map extends up over the game view.
		int mapX = (container.getWidth() - MAP_SPRITE_WIDTH) / 2;
		int mapY = container.getHeight() - MAP_SPRITE_HEIGHT;

		this.createSpriteWidget(container, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID);
		this.createCarpetWidgets(container, currentStation, options, mapX, mapY);
		this.createCloseButton(container, options, mapX, mapY);
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

					UIHotkey hotkey = this.createHotKey(container, definition.getHotkey(), String.valueOf(hotkeyDigit));
					hotkey.setPosition(mapX + definition.getHotkey().getX(), mapY + definition.getHotkey().getY());
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
			if (option.text.startsWith(DECLINE_OPTION_PREFIX))
			{
				declineOption = option;
				break;
			}
		}

		if (declineOption == null)
			return;

		final DialogOption decline = declineOption;
		Widget closeWidget = container.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(mapX + MAP_SPRITE_WIDTH - CLOSE_BUTTON_WIDTH - 4, mapY + 4);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.triggerTravel(decline));
		closeWidget.revalidate();
	}

	/**
	 * Selects the given dialog option by re-firing the listener the game
	 * attached to the original (now hidden) option widget
	 */
	private void triggerTravel(DialogOption option)
	{
		this.clientThread.invokeLater(() ->
		{
			Object[] opListener = option.widget.getOnOpListener();
			if (opListener != null)
			{
				this.client.runScript(opListener);
				return;
			}

			Object[] keyListener = option.widget.getOnKeyListener();
			if (keyListener != null)
			{
				this.client.runScript(keyListener);
				return;
			}

			log.debug("No listener found on dialog option '{}'", option.text);
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
