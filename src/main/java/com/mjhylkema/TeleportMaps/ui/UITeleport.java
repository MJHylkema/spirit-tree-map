package com.mjhylkema.TeleportMaps.ui;

import static java.lang.Integer.max;
import static java.lang.Integer.min;
import net.runelite.api.widgets.Widget;

public class UITeleport extends UIComponent
{
	private int spriteStandard;
	private int spriteHover;
	private int spriteDisabled;

	private boolean locked;

	private UIButton teleportButton;
	private UIHotkey hotkeyButton;
	private UILabel label;

	public UITeleport(Widget groupWidget, Widget teleport)
	{
		super(groupWidget);
		this.setOnHoverListener(this::onHover);
		this.setOnLeaveListener(this::onLeave);

		this.teleportButton = new UIButton(teleport);
	}

	@Override
	public void setPosition(int x, int y)
	{
		this.teleportButton.setPosition(x, y);
		this.revalidatePosition();
	}

	@Override
	public void setSize(int width, int height)
	{
		this.teleportButton.setSize(width, height);
		this.revalidateSize();
	}

	protected void onHover(UIComponent src)
	{
		this.teleportButton.getWidget().setSpriteId(this.teleportButton.getSpriteHover());
		this.teleportButton.getWidget().revalidate();
	}

	protected void onLeave(UIComponent src)
	{
		this.teleportButton.getWidget().setSpriteId(this.teleportButton.getSpriteStandard());
		this.teleportButton.getWidget().revalidate();
	}

	public void setLocked(boolean locked)
	{
		this.locked = locked;

		if (locked)
			this.teleportButton.setSprites(this.spriteDisabled, this.spriteDisabled);
		else
			this.teleportButton.setSprites(this.spriteStandard, this.spriteHover);
	}

	public void attachHotkey(UIHotkey hotkeyButton)
	{
		this.hotkeyButton = hotkeyButton;
		this.revalidatePosition();
		this.revalidateSize();
	}

	public void attachLabel(UILabel label)
	{
		this.label = label;
	}

	public void setLabelVisibility(boolean visible)
	{
		if (this.label != null)
			this.label.setVisibility(visible);
	}

	public void setHotkeyInLabel(boolean value)
	{
		if (this.label != null)
			this.label.setShowHotkey(value);
	}

	public void setHotKeyVisibility(boolean visible)
	{
		if (this.hotkeyButton != null)
		{
			this.hotkeyButton.setVisibility(visible);
			this.revalidatePosition();
			this.revalidateSize();
		}
	}

	public void setTeleportSprites(int standard, int hover, int disabled)
	{
		this.spriteStandard = standard;
		this.spriteHover = hover;
		this.spriteDisabled = disabled;

		if (locked)
			this.teleportButton.setSprites(this.spriteDisabled, this.spriteDisabled);
		else
			this.teleportButton.setSprites(this.spriteStandard, this.spriteHover);
	}

	public void revalidatePosition()
	{
		if (this.hotkeyButton != null && this.hotkeyButton.isVisible())
		{
			super.setPosition(
				min(this.teleportButton.getX(), this.hotkeyButton.getX()),
				min(this.teleportButton.getY(), this.hotkeyButton.getY()));
		}
		else
		{
			super.setPosition(this.teleportButton.getX(), this.teleportButton.getY());
		}

		this.getWidget().revalidate();
	}

	public void revalidateSize()
	{
		if (this.hotkeyButton != null && this.hotkeyButton.isVisible())
		{
			super.setSize(
				max(
					this.teleportButton.getXTotal(),
					this.hotkeyButton.getXTotal()
				) - min(this.teleportButton.getX(), this.hotkeyButton.getX()),
				max(
					this.teleportButton.getYTotal(),
					this.hotkeyButton.getYTotal()
				) - min(this.teleportButton.getY(), this.hotkeyButton.getY())
			);
		}
		else
		{
			super.setSize(this.teleportButton.getWidth(), this.teleportButton.getHeight());
		}

		this.getWidget().revalidate();
	}
}
