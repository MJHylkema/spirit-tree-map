package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class XericsDefinition
{
	@Getter
	static private int width = 23;
	@Getter
	static private int height = 23;

	private String name;
	private int x;
	private int y;
	private HotKeyDefinition hotkey;
	private LabelDefinition label;
}