package com.mjhylkema.TeleportMaps.ui;

import net.runelite.api.widgets.Widget;

/**
 * This class wraps a game widget and gives it the functionality
 * of a button, with the option of a second sprite shown on hover.
 * Credit to Antipixel for the original implementation
 */
public class UIButton extends UIComponent
{
	private int spriteStandard;
	private int spriteHover;

	/**
	 * Constructs a new button component
	 * @param widget the underlying widget
	 */
	public UIButton(Widget widget)
	{
		super(widget);

		// Blank the sprites
		this.spriteStandard = -1;
		this.spriteHover = -1;
	}

	/**
	 * Sets the button sprite for both standard and hover
	 * @param standard the standard sprite id
	 * @param hover the sprite to display on hover
	 */
	public void setSprites(int standard, int hover)
	{
		this.spriteStandard = standard;
		this.spriteHover = hover;

		// Update the widgets sprite
		this.getWidget().setSpriteId(this.spriteStandard);
	}

	public int getSpriteStandard()
	{
		return spriteStandard;
	}

	public int getSpriteHover()
	{
		return spriteHover;
	}
}