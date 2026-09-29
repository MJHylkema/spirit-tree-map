package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;

public abstract class BaseMap implements IMap
{
	protected static final int HOTKEY_LABEL_SPRITE_ID = -19002;

	protected TeleportMapsPlugin plugin;
	protected TeleportMapsConfig config;
	protected Client client;
	protected ClientThread clientThread;
	final protected List<UITeleport> activeUITeleports;
	private boolean active;

	public BaseMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread, boolean active)
	{
		this.plugin = plugin;
		this.config = config;
		this.client = client;
		this.clientThread = clientThread;
		this.active = active;
		this.activeUITeleports = new ArrayList<>();
	}

	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
				this.updateTeleports((teleport) -> teleport.setHotKeyVisibility(config.displayHotkeys()));
			default:
				return;
		}
	}

	protected void updateTeleports(Consumer<UITeleport> action)
	{
		if (this.activeUITeleports.size() == 0)
			return;

		this.clientThread.invokeLater(() -> this.activeUITeleports.forEach(action));
	}

	public boolean isActive()
	{
		return this.active;
	}

	protected void setActive(boolean active)
	{
		this.active = active;
	}

	protected void addTeleport(UITeleport teleport)
	{
		this.activeUITeleports.add(teleport);
	}

	protected void clearTeleports()
	{
		this.activeUITeleports.clear();
	}

	protected void runPacketSendingScript(Object... args)
	{
		this.client.createScriptEventBuilder(args)
			.build()
			.setCanSendPackets(true)
			.run();
	}

	protected Widget createSpriteWidget(Widget parent, int spriteWidth, int spriteHeight, int originalX, int originalY, int spriteId)
	{
		// Create a graphic widget
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(spriteWidth);
		widget.setOriginalHeight(spriteHeight);
		widget.setOriginalX(originalX);
		widget.setOriginalY(originalY);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	protected UIHotkey createHotKey(Widget container, HotKeyDefinition hotKeyDefinition, String hotKeyLabel)
	{
		return this.createHotKey(container, hotKeyDefinition, hotKeyLabel, 0, 0);
	}

	/**
	 * Variant of {@link #createHotKey(Widget, HotKeyDefinition, String)} for maps
	 * built at an offset within their container
	 */
	protected UIHotkey createHotKey(Widget container, HotKeyDefinition hotKeyDefinition, String hotKeyLabel, int offsetX, int offsetY)
	{
		Widget icon = container.createChild(-1, WidgetType.GRAPHIC);
		icon.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
		Widget text = container.createChild(-1, WidgetType.TEXT);

		UIHotkey hotkey = new UIHotkey(icon, text);

		boolean displayHotkeys = this.config.displayHotkeys();

		hotkey.setSize(hotKeyDefinition.getWidth(), hotKeyDefinition.getHeight());
		hotkey.setPosition(offsetX + hotKeyDefinition.getX(), offsetY + hotKeyDefinition.getY());
		hotkey.setText(hotKeyLabel);
		hotkey.setVisibility(displayHotkeys);

		return hotkey;
	}

	/**
	 * Finds the "mainmodal" layer for the current display mode: the layer
	 * over the game view that the game opens screen-level modals into
	 */
	protected Widget getScreenContainer()
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

	protected void setWidgetsHidden(int groupID, int[] childIDs, boolean hidden)
	{
		for(int childId : childIDs)
		{
			Widget widget = this.client.getWidget(groupID, childId);
			if (widget != null)
			{
				widget.setHidden(hidden);
			}
		}
	}
}
