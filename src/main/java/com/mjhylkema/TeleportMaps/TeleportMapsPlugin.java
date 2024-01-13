package com.mjhylkema.TeleportMaps;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.components.MushtreeMap;
import com.mjhylkema.TeleportMaps.components.SpiritTreeMap;
import com.mjhylkema.TeleportMaps.definition.SpriteDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Teleport Maps",
	configName = "SpiritTreeMapPlugin", // Original plugin name
	enabledByDefault = true
)
public class TeleportMapsPlugin extends Plugin
{
	private static final String DEF_FILE_SPRITES = "/SpriteDefinitions.json";

	@Inject
	private Gson gson;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	@Getter
	private ClientThread clientThread;

	@Inject
	@Getter
	private Client client;

	@Inject
	@Getter
	private TeleportMapsConfig config;

	private List<BaseMap> mapComponents;

	@Override
	protected void startUp()
	{
		SpriteDefinition[] spriteDefinitions = this.loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES);
		this.spriteManager.addSpriteOverrides(spriteDefinitions);

		this.mapComponents = new ArrayList<>();

		this.populateMaps();
	}

	public  <T> T loadDefinitionResource(Class<T> classType, String resource)
	{
		// Load the resource as a stream and wrap it in a reader
		InputStream resourceStream = classType.getResourceAsStream(resource);
		InputStreamReader definitionReader = new InputStreamReader(resourceStream);

		// Load the objects from the JSON file
		return gson.fromJson(definitionReader, classType);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded e)
	{
		this.mapComponents.forEach((baseMap -> baseMap.widgetLoaded(e)));
	}

	@Provides
	TeleportMapsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TeleportMapsConfig.class);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
				updateHotkeyLabels();
				break;
			case TeleportMapsConfig.KEY_SHOW_SPIRIT_TREE_MAP:
			case TeleportMapsConfig.KEY_SHOW_MUSHTREE_MAP:
				populateMaps();
				break;
			default:
				return;
		}
	}

	private void updateHotkeyLabels()
	{
		boolean visible = config.displayHotkeys();
		this.mapComponents.forEach((baseMap -> baseMap.changeHotkeyVisibility(visible)));
	}

	private void populateMaps()
	{
		this.mapComponents.clear();

		if (this.config.showSpiritTreeMap())
			this.mapComponents.add(new SpiritTreeMap(this));

		if (this.config.showMushtreeMap())
			this.mapComponents.add(new MushtreeMap(this));
	}
}
