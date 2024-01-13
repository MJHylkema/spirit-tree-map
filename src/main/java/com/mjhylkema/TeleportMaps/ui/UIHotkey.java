package com.mjhylkema.TeleportMaps.ui;

import net.runelite.api.widgets.Widget;

public class UIHotkey extends UIComponent
{
	private boolean visible = true;
	private UILabel label;

	public UIHotkey(Widget backgroundWidget, Widget labelWidget)
	{
		super(backgroundWidget);

		this.label = new UILabel(labelWidget);
	}

	@Override
	public void setX(int x)
	{
		// Match the position of the label
		// to that of the checkbox
		super.setX(x);
		this.label.setX(x);
	}

	@Override
	public void setY(int y)
	{
		// Match the position of the label
		// to that of the checkbox
		super.setY(y);
		this.label.setY(y + 1);
	}

	@Override
	public void setSize(int width, int height)
	{
		super.setSize(width, height);
		this.label.setSize(width, height);
	}

	public void setText(String text)
	{
		this.label.setText(text);
	}

	public void setVisibility(boolean visible)
	{
		this.visible = visible;
		super.setVisibility(visible);
		this.label.setVisibility(visible);
	}

	public boolean isVisible()
	{
		return this.visible;
	}
}
