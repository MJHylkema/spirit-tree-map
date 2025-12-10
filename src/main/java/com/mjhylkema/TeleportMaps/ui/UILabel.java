package com.mjhylkema.TeleportMaps.ui;

import net.runelite.api.FontID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

public class UILabel extends UIComponent
{
	protected static final int HOTKEY_LABEL_COLOR = 3287045; /*322805*/

	private String hotkey;
	private String labelText;

	public UILabel(Widget labelWidget)
	{
		super(labelWidget);

		// Set default font and text colour
		this.setFont(FontID.PLAIN_11);
		this.setColour(HOTKEY_LABEL_COLOR);

		// Set the alignment to centre and enable text shadowing
		labelWidget.setXTextAlignment(WidgetTextAlignment.CENTER);
		labelWidget.setYTextAlignment(WidgetTextAlignment.CENTER);
	}

	public void setHotkey(String hotkey)
	{
		this.hotkey = hotkey;
	}

	public void setShowHotkey(boolean visible)
	{
		if (visible && !this.hotkey.isEmpty())
			this.getWidget().setText(this.hotkey + ". " + this.labelText);
		else
			this.getWidget().setText(this.labelText);

		this.getWidget().revalidate();
	}

	public void setText(String text)
	{
		this.labelText = text;
		this.getWidget().setText(text);
	}

	public void setFont(int fontID)
	{
		this.getWidget().setFontId(fontID);
	}

	public void setColour(int colour)
	{
		this.getWidget().setTextColor(colour);
	}
}