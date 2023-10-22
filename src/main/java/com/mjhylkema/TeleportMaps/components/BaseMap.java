package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.FontID;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
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

	protected void createHotKeyLabel(Widget container, HotKeyDefinition hotKeyDefinition, String hotKeyLabel)
	{
		boolean displayHotkeys = this.plugin.getConfig().displayHotkeys();

		Widget hotKeyWidget = this.createSpriteWidget(container,
			HOTKEY_LABEL_SPRITE_WIDTH,
			HOTKEY_LABEL_SPRITE_HEIGHT,
			hotKeyDefinition.getX(),
			hotKeyDefinition.getY(),
			HOTKEY_LABEL_SPRITE_ID);
		hotKeyWidget.setHidden(!displayHotkeys);
		this.addHotkeyLabel(hotKeyWidget);

		if (displayHotkeys)
			hotKeyWidget.revalidate();

		Widget hotKeyText = container.createChild(-1, WidgetType.TEXT);
		hotKeyText.setText(hotKeyLabel);
		hotKeyText.setFontId(FontID.QUILL_8);
		hotKeyText.setTextColor(HOTKEY_LABEL_COLOR);
		hotKeyText.setOriginalWidth(HOTKEY_LABEL_SPRITE_WIDTH);
		hotKeyText.setOriginalHeight(HOTKEY_LABEL_SPRITE_HEIGHT);
		hotKeyText.setOriginalX(hotKeyDefinition.getX() + 1);
		hotKeyText.setOriginalY(hotKeyDefinition.getY() + 1);
		hotKeyText.setXTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setYTextAlignment(WidgetTextAlignment.CENTER);
		hotKeyText.setHidden(!displayHotkeys);
		this.addHotkeyLabel(hotKeyText);

		if (displayHotkeys)
			hotKeyText.revalidate();
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
