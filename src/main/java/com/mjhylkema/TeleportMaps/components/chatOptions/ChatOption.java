package com.mjhylkema.TeleportMaps.components.chatOptions;

import java.awt.Color;
import lombok.Getter;
import net.runelite.api.widgets.Widget;

/**
 * A selectable option in the currently open chat options dialog
 */
@Getter
public class ChatOption
{
	private final Widget widget;
	/* The option's position in the dialog, natively bound to its number key */
	private final int number;
	/* The option text with any formatting tags removed */
	private final String text;
	private final Runnable selector;
	/* The colour the game drew the option in, restored when unhighlighted */
	private final int originalTextColor;

	ChatOption(Widget widget, int number, String text, Runnable selector)
	{
		this.widget = widget;
		this.number = number;
		this.text = text;
		this.selector = selector;
		this.originalTextColor = widget.getTextColor();
	}

	/**
	 * Selects this option, as if its number key had been pressed
	 */
	public void select()
	{
		this.selector.run();
	}

	/**
	 * Highlights the option text in the dialog, mirroring a hovered map icon
	 */
	public void setHighlighted(boolean highlighted)
	{
		this.widget.setTextColor(highlighted ? Color.WHITE.getRGB() : this.originalTextColor);
	}
}
