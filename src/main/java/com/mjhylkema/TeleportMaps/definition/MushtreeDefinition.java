package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class MushtreeDefinition
{
	@Getter
	static private int width = 30;
	@Getter
	static private int height = 35;

	private String name;
	private int x;
	private int y;
	private HotKeyDefinition hotkey;
	private GameObjectDefinition mushtreeObject;
}
