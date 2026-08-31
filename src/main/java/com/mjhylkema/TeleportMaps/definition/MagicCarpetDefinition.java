package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class MagicCarpetDefinition
{
	@Getter
	static private final int width = 15;
	@Getter
	static private final int height = 24;

	private String name;
	private int x;
	private int y;
	private int worldPointX;
	private int worldPointY;
	private HotKeyDefinition hotkey;
	private TravelOptionDefinition[] travelOptions;
}
