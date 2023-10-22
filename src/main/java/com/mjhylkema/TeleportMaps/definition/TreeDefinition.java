package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class TreeDefinition
{
	private String name;
	private int x;
	private int y;
	private int width;
	private int height;
	private int spriteEnabled;
	private int spriteHover;
	private HotKeyDefinition hotkey;
}
