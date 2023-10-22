package com.mjhylkema.TeleportMaps.ui;

import com.mjhylkema.TeleportMaps.definition.MushtreeDefinition;
import lombok.Getter;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.Keybind;

@Getter
public class Mushtree
{
	private Widget widget;
	private MushtreeDefinition definition;
	private Keybind hotkey;

	public Mushtree(MushtreeDefinition definition, Widget widget, Keybind hotkey)
	{
		this.definition = definition;
		this.widget = widget;
		this.hotkey = hotkey;
	}
}