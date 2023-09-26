package com.mjhylkema.SpiritTreeMap.ui;

import com.mjhylkema.SpiritTreeMap.definition.TreeDefinition;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import net.runelite.api.ScriptEvent;
import net.runelite.api.widgets.JavaScriptCallback;
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
