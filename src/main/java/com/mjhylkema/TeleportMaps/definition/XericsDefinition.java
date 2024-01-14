package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class XericsDefinition extends AdventureLogEntryDefinition
{
	@Getter
	static private int width = 23;
	@Getter
	static private int height = 23;

	private LabelDefinition label;
}