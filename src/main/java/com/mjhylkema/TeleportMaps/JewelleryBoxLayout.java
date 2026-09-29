package com.mjhylkema.TeleportMaps;

import lombok.AllArgsConstructor;

@AllArgsConstructor
public enum JewelleryBoxLayout
{
	SINGLE_MAP("Single map"),
	TABS("Tabs");

	private final String name;

	@Override
	public String toString()
	{
		return this.name;
	}
}
