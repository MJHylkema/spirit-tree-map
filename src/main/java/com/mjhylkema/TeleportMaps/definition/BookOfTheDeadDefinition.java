package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class BookOfTheDeadDefinition
{
	@Getter
	static private int width = 28;
	@Getter
	static private int height = 25;

	/* The destination as named in the dialog option, e.g. "Hosidius" */
	private String name;
	private int x;
	private int y;
	private LabelDefinition label;
}
