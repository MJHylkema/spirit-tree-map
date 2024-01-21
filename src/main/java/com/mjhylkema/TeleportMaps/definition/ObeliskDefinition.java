package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class ObeliskDefinition extends AdventureLogEntryDefinition
{
	@Getter
	static private int width = 20;
	@Getter
	static private int height = 45;
	private int spriteEnabled;
	private int spriteHover;
	private int spriteSelected;
	private int obeliskObjectId;
	private LabelDefinition label;
}
