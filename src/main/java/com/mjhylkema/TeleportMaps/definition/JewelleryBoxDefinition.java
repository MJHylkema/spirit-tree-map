package com.mjhylkema.TeleportMaps.definition;

import lombok.Getter;

@Getter
public class JewelleryBoxDefinition
{
	@Getter
	static private final int width = 28;
	@Getter
	static private final int height = 25;

	private String name;
	/* The marker's centre, on the full size Spirit Tree map */
	private int x;
	private int y;
	/* Where the tabbed layout labels this destination: "above", "left" or
	   "right" of its marker; below if not given */
	private String label;
}
