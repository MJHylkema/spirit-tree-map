package com.mjhylkema.TeleportMaps.components.chatOptions;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.BookOfTheDeadDefinition;
import com.mjhylkema.TeleportMaps.ui.UILabel;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
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

/**
 * Map for the "Reminisce" teleports of the Book of the dead and
 * Kharedst's memoirs, which share the same dialog
 */
public class BookOfTheDeadMap extends BaseMap implements IChatOptionsMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_BOOK_OF_THE_DEAD = "/BookOfTheDeadMap/BookOfTheDeadDefinitions.json";

	/* Sprite IDs, dimensions and positions. The Kourend map is shared with
	   Xeric's map and drawn at the same position within the frame. */
	private static final int MAP_SPRITE_ID = -19400;
	private static final int MAP_SPRITE_WIDTH = 509;
	private static final int MAP_SPRITE_HEIGHT = 317;
	private static final int MAP_SPRITE_X = 2;
	private static final int MAP_SPRITE_Y = 13;
	/* A lancallium rose, after the "Lunch by the Lancalliums" page */
	private static final int ROSE_SPRITE_ID = -19900;
	private static final int ROSE_HIGHLIGHTED_SPRITE_ID = -19901;
	private static final int ROSE_DISABLED_SPRITE_ID = -19902;

	private static final String DIALOG_TITLE = "What would you like to remember?";
	/* Matches an option such as "'Lunch by the Lancalliums' - Hosidius";
	   group 1 is the memoir page and group 2 the destination */
	private static final Pattern OPTION_PATTERN = Pattern.compile("'(.+)' - (.+)");
	private static final String TRAVEL_ACTION = "Travel";
	private static final String EXAMINE_ACTION = "Examine";

	private BookOfTheDeadDefinition[] definitions;

	@Inject
	public BookOfTheDeadMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread)
	{
		super(plugin, config, client, clientThread, config.showBookOfTheDeadMap());
		this.definitions = this.plugin.loadDefinitionResource(BookOfTheDeadDefinition[].class, DEF_FILE_BOOK_OF_THE_DEAD);
	}

	@Override
	public boolean matchesDialog(String title, List<ChatOption> options)
	{
		return DIALOG_TITLE.equals(title);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_BOOK_OF_THE_DEAD_MAP:
				this.setActive(config.showBookOfTheDeadMap());
				break;
			case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
				this.updateTeleports((teleport) -> teleport.setHotkeyInLabel(config.displayHotkeys()));
				break;
			default:
				super.onConfigChanged(e);
		}
	}

	@Override
	public void buildInterface(Widget container, int x, int y, List<ChatOption> options)
	{
		Widget map = this.createSpriteWidget(container,
			MAP_SPRITE_WIDTH,
			MAP_SPRITE_HEIGHT,
			x + MAP_SPRITE_X,
			y + MAP_SPRITE_Y,
			MAP_SPRITE_ID);

		// Set hasListener / noClickThrough to disallow click-through
		map.setHasListener(true);
		map.setNoClickThrough(true);

		this.createTeleportWidgets(container, x, y, this.buildDestinationLookup(options));
	}

	@Override
	public void clearInterface()
	{
		this.clearTeleports();
	}

	/**
	 * Maps each destination offered by the dialog to its option.
	 * Pages not yet added to the book aren't offered.
	 */
	private HashMap<String, ChatOption> buildDestinationLookup(List<ChatOption> options)
	{
		HashMap<String, ChatOption> destinations = new HashMap<>();

		for (ChatOption option : options)
		{
			Matcher matcher = OPTION_PATTERN.matcher(option.getText());
			if (matcher.matches())
				destinations.put(matcher.group(2), option);
		}

		return destinations;
	}

	private void createTeleportWidgets(Widget container, int x, int y, HashMap<String, ChatOption> destinations)
	{
		this.clearTeleports();

		for (BookOfTheDeadDefinition definition : this.definitions)
		{
			Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
			Widget teleportWidget = container.createChild(-1, WidgetType.GRAPHIC);

			UITeleport teleport = new UITeleport(widgetContainer, teleportWidget);

			teleport.setPosition(x + definition.getX(), y + definition.getY());
			teleport.setTeleportSprites(ROSE_SPRITE_ID, ROSE_HIGHLIGHTED_SPRITE_ID, ROSE_DISABLED_SPRITE_ID);
			teleport.setSize(BookOfTheDeadDefinition.getWidth(), BookOfTheDeadDefinition.getHeight());
			teleport.setName(definition.getName());

			UILabel teleportLabel = new UILabel(container.createChild(-1, WidgetType.TEXT));
			teleportLabel.getWidget().setTextColor(Color.white.getRGB());
			teleportLabel.getWidget().setTextShadowed(true);
			teleportLabel.setText(definition.getLabel().getTitle());
			teleportLabel.setPosition(x + definition.getLabel().getX(), y + definition.getLabel().getY());
			teleportLabel.setSize(definition.getLabel().getWidth(), definition.getLabel().getHeight());
			teleport.attachLabel(teleportLabel);

			ChatOption option = destinations.get(definition.getName());

			if (option != null)
			{
				teleport.addAction(TRAVEL_ACTION, option::select);
				teleport.addOnHoverListener((listener) -> option.setHighlighted(true));
				teleport.addOnLeaveListener((listener) -> option.setHighlighted(false));

				// The dialog options natively respond to their number key
				teleportLabel.setHotkey(String.valueOf(option.getNumber()));
				teleport.setHotkeyInLabel(config.displayHotkeys());
			}
			else
			{
				teleportLabel.setHotkey("");
				teleport.setLocked(true);
				teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
			}

			this.addTeleport(teleport);
		}
	}

	private void triggerLockedMessage(BookOfTheDeadDefinition definition)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You haven't added the page for %s to the book yet.", definition.getName()), null));
	}
}
