package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

public abstract class BaseMap implements IMap
{
	protected static final int HOTKEY_LABEL_SPRITE_ID = -19002;

	protected TeleportMapsPlugin plugin;
	private List<UITeleport> activeTeleports;
	protected boolean active = false;

	BaseMap(TeleportMapsPlugin plugin, boolean active)
	{
		this.plugin = plugin;
		this.active = active;
		this.activeTeleports = new ArrayList<>();
	}

	public abstract void onWidgetLoaded(WidgetLoaded e);

	public void changeHotkeyVisibility(boolean visible)
	{
		if (this.activeTeleports.size() == 0)
			return;

		this.plugin.getClientThread().invokeLater(() -> {
			this.activeTeleports.forEach((teleport) -> {
				teleport.setHotKeyVisibility(visible);
			});
		});
	}

	public boolean isActive()
	{
		return active;
	}

	public abstract void setActive(String key, boolean active);

	protected void addTeleport(UITeleport teleport)
	{
		this.activeTeleports.add(teleport);
	}

	protected void clearTeleports()
	{
		this.activeTeleports.clear();
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

		boolean displayHotkeys = this.plugin.getConfig().displayHotkeys();

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
			Widget widget = this.plugin.getClient().getWidget(groupID, childId);
			if (widget != null)
			{
				widget.setHidden(hidden);
			}
		}
	}
}
