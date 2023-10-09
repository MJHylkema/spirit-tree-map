package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;

public abstract class BaseMap
{
	protected static final int HOTKEY_LABEL_SPRITE_ID = -19002;
	protected static final int HOTKEY_LABEL_SPRITE_WIDTH = 20;
	protected static final int HOTKEY_LABEL_SPRITE_HEIGHT = 19;

	protected static final int HOTKEY_LABEL_COLOR = 3287045; /*322805*/

	protected TeleportMapsPlugin plugin;
	private List<Widget> activeHotkeyLabels;

	BaseMap(TeleportMapsPlugin plugin)
	{
		this.plugin = plugin;
		this.activeHotkeyLabels = new ArrayList<>();
	}

	public abstract void widgetLoaded(WidgetLoaded e);

	public void changeHotkeyLabelVisibility(boolean visible)
	{
		if (this.activeHotkeyLabels.size() == 0)
			return;

		this.plugin.getClientThread().invokeLater(() -> {
			this.activeHotkeyLabels.forEach((label) -> {
				label.setHidden(!visible);
				label.revalidate();
			});
		});
	}

	public abstract void changeLabelVisibility();

	protected void addHotkeyLabel(Widget label)
	{
		this.activeHotkeyLabels.add(label);
	}

	protected void clearHotKeyLabels()
	{
		this.activeHotkeyLabels.clear();
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
}
