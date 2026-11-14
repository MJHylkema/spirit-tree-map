package com.mjhylkema.TeleportMaps.components.chatOptions;

import com.mjhylkema.TeleportMaps.components.IMap;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.KeyCode;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.Text;

/**
 * Replaces chat options dialogs ("Select an Option" style menus in the
 * chatbox) with a map. The chatbox is too small to hold a map, so maps are
 * built over the game view instead, in a frame the size of the adventure log.
 */
@Slf4j
public class ChatOptionsComposite implements IMap
{
	private static final int DIALOG_OPTION_GROUP_ID = 219;
	private static final int DIALOG_OPTION_CONTAINER_CHILD = 1;

	/* The keyboard digit for the first dialog option; options natively respond
	   to the number keys matching their position in the dialog */
	private static final int OPTION_KEY_CHAR_BASE = '0';

	/* Maps are built into a frame matching the adventure log's dimensions,
	   so adventure log map art and coordinates can be reused */
	private static final int FRAME_WIDTH = 512;
	private static final int FRAME_HEIGHT = 334;

	private static final int IF_CLOSE_SCRIPT_ID = 29;
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGHT = 23;
	private static final int CLOSE_BUTTON_X = 428;
	private static final int CLOSE_BUTTON_Y = 22;

	final private List<IChatOptionsMap> chatOptionsMaps;
	final private Client client;
	final private ClientThread clientThread;

	/* The map currently built over the game view, if any */
	private IChatOptionsMap activeMap;

	@Inject
	public ChatOptionsComposite(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatOptionsMaps = new ArrayList<>();
	}

	public void addChatOptionsMap(IChatOptionsMap map)
	{
		this.chatOptionsMaps.add(map);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		if (e.getGroupId() != DIALOG_OPTION_GROUP_ID || !this.isActive())
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
		// The map's own config handler may not have updated its active state yet,
		// so check on the next client cycle
		this.clientThread.invokeLater(() ->
		{
			if (this.activeMap != null && !this.activeMap.isActive())
				this.destroyInterface();
		});
	}

	private void tryBuildInterface()
	{
		Widget dialog = this.client.getWidget(DIALOG_OPTION_GROUP_ID, DIALOG_OPTION_CONTAINER_CHILD);
		if (dialog == null)
			return;

		String title = null;
		List<ChatOption> options = new ArrayList<>();

		// Only the selectable options carry key listeners; the title doesn't
		Widget[] children = dialog.getDynamicChildren();
		for (int i = 0; i < children.length; i++)
		{
			Widget child = children[i];
			String text = child.getText();

			if (text == null || text.isEmpty())
				continue;

			if (child.getOnKeyListener() == null)
			{
				if (title == null)
					title = Text.removeTags(text);
				continue;
			}

			options.add(this.createOption(child, i, Text.removeTags(text)));
		}

		if (options.isEmpty())
			return;

		for (IChatOptionsMap map : this.chatOptionsMaps)
		{
			if (map.isActive() && map.matchesDialog(title, options))
			{
				this.buildInterface(map, options);
				break;
			}
		}
	}

	private void buildInterface(IChatOptionsMap map, List<ChatOption> options)
	{
		Widget screen = this.getScreenContainer();
		if (screen == null)
			return;

		int x = (screen.getWidth() - FRAME_WIDTH) / 2;
		int y = Math.max(0, (screen.getHeight() - FRAME_HEIGHT) / 2);

		this.activeMap = map;
		map.buildInterface(screen, x, y, options);
		this.createCloseButton(screen, x, y);
	}

	/**
	 * Removes the map. Unlike the adventure log maps, these widgets aren't
	 * children of the dialog, so they outlive it and must be removed when
	 * the dialog closes or the plugin shuts down. Must run on the client thread.
	 */
	public void destroyInterface()
	{
		if (this.activeMap == null)
			return;

		Widget screen = this.getScreenContainer();
		if (screen != null)
			screen.deleteAllChildren();

		this.activeMap.clearInterface();
		this.activeMap = null;
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

	/**
	 * Creates a close button on the map frame that closes the dialog
	 */
	private void createCloseButton(Widget screen, int x, int y)
	{
		Widget closeWidget = screen.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(x + CLOSE_BUTTON_X, y + CLOSE_BUTTON_Y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.clientThread.invokeLater(() -> this.client.createScriptEventBuilder(IF_CLOSE_SCRIPT_ID)
			.build()
			.setCanSendPackets(true)
			.run()));
		closeWidget.revalidate();
	}

	private ChatOption createOption(Widget widget, int number, String text)
	{
		return new ChatOption(widget, number, text, () -> this.selectOption(widget, number));
	}

	/**
	 * Selects a dialog option by dispatching the key listener the game
	 * attached to the option widget, with the event placeholders filled in
	 * as if the option's number key had been pressed
	 */
	private void selectOption(Widget widget, int number)
	{
		if (number < 1 || number > 9)
			return;

		this.clientThread.invokeLater(() ->
		{
			Object[] template = widget.getOnKeyListener();
			if (template == null)
			{
				log.debug("No key listener on dialog option {}", number);
				return;
			}

			Object[] listener = new Object[template.length];
			for (int i = 0; i < template.length; i++)
			{
				Object arg = template[i];
				if (arg instanceof Integer)
				{
					switch ((Integer) arg)
					{
						case ScriptEvent.KEY_CODE:
							arg = KeyCode.KC_1 + number - 1;
							break;
						case ScriptEvent.KEY_CHAR:
							arg = OPTION_KEY_CHAR_BASE + number;
							break;
						case ScriptEvent.WIDGET_ID:
							arg = widget.getId();
							break;
						case ScriptEvent.WIDGET_INDEX:
							arg = widget.getIndex();
							break;
						default:
							break;
					}
				}
				listener[i] = arg;
			}

			this.client.createScriptEventBuilder(listener)
				.build()
				.setCanSendPackets(true)
				.run();
		});
	}

	@Override
	public boolean isActive()
	{
		for (IChatOptionsMap map : this.chatOptionsMaps)
		{
			if (map.isActive())
				return true;
		}
		return false;
	}
}
