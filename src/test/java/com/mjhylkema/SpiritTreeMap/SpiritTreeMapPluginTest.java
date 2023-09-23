package com.mjhylkema.SpiritTreeMap;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class SpiritTreeMapPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SpiritTreeMapPlugin.class);
		RuneLite.main(args);
	}
}