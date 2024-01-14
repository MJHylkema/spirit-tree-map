package com.mjhylkema.TeleportMaps.ui;

import com.mjhylkema.TeleportMaps.definition.AdventureLogEntryDefinition;
import lombok.Getter;
import net.runelite.api.widgets.Widget;

@Getter
public class AdventureLogEntry
{
	private Widget widget;
	private AdventureLogEntryDefinition definition;
	private String keyShortcut;

	public AdventureLogEntry(AdventureLogEntryDefinition definition, Widget widget, String shortcut)
	{
		this.definition = definition;
		this.widget = widget;
		this.keyShortcut = shortcut;
	}
}
