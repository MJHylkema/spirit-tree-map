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
		Widget icon = container.createChild(-1, WidgetType.GRAPHIC);
		icon.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
		Widget text = container.createChild(-1, WidgetType.TEXT);

		UIHotkey hotkey = new UIHotkey(icon, text);

		boolean displayHotkeys = this.config.displayHotkeys();

		hotkey.setSize(hotKeyDefinition.getWidth(), hotKeyDefinition.getHeight());
		hotkey.setPosition(hotKeyDefinition.getX(), hotKeyDefinition.getY());
		hotkey.setText(hotKeyLabel);
		hotkey.setVisibility(displayHotkeys);

		return hotkey;
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
