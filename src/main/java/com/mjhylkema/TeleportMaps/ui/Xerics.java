package com.mjhylkema.TeleportMaps.ui;

import com.mjhylkema.TeleportMaps.definition.XericsDefinition;
import lombok.Getter;
import net.runelite.api.widgets.Widget;

@Getter
public class Xerics
{
	private Widget widget;
	private XericsDefinition definition;
	private String keyShortcut;

	public Xerics(XericsDefinition definition, Widget widget, String shortcut)
	{
		this.definition = definition;
		this.widget = widget;
		this.keyShortcut = shortcut;
	}
}
