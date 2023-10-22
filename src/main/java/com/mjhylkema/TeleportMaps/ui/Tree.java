package com.mjhylkema.TeleportMaps.ui;

import com.mjhylkema.TeleportMaps.definition.TreeDefinition;
import lombok.Getter;
import net.runelite.api.widgets.Widget;

@Getter
public class Tree
{
	private Widget widget;
	private TreeDefinition definition;
	private String keyShortcut;
	private String displayedName;

	public Tree(TreeDefinition definition, Widget widget, String shortcut, String displayedName)
	{
		this.definition = definition;
		this.widget = widget;
		this.keyShortcut = shortcut;
		this.displayedName = displayedName;
	}
}
