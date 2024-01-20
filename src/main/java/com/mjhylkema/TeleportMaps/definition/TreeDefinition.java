package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class TreeDefinition extends AdventureLogEntryDefinition
{
	private int id;
	private int width;
	private int height;
	private int spriteEnabled;
	private int spriteHover;
	private int spriteSelected;
	private GameObjectDefinition treeObject;
}
