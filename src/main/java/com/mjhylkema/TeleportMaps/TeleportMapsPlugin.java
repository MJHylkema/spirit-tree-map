package com.mjhylkema.TeleportMaps;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mjhylkema.TeleportMaps.components.adventureLog.AdventureLogComposite;
import com.mjhylkema.TeleportMaps.components.IMap;
import com.mjhylkema.TeleportMaps.components.adventureLog.MinecartMap;
import com.mjhylkema.TeleportMaps.components.MushtreeMap;
import com.mjhylkema.TeleportMaps.components.adventureLog.SkillsNecklaceMap;
import com.mjhylkema.TeleportMaps.components.adventureLog.SpiritTreeMap;
import com.mjhylkema.TeleportMaps.components.adventureLog.WildernessObeliskMap;
import com.mjhylkema.TeleportMaps.components.adventureLog.XericsMap;
import com.mjhylkema.TeleportMaps.definition.SpriteDefinition;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
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
	private EventBus eventBus;

	@Inject
	@Getter
	private TeleportMapsConfig config;

	@Inject
	private MushtreeMap mushtreeMap;
	@Inject
	private SpiritTreeMap spiritTreeMap;
	@Inject
	private XericsMap xericsMap;
	@Inject
	private MinecartMap minecartMap;
	@Inject
	private WildernessObeliskMap obeliskMap;
	@Inject
	private SkillsNecklaceMap skillsNecklaceMap;
	@Inject
	AdventureLogComposite adventureLogComposite;

	private List<IMap> mapComponents;

	@Override
	protected void startUp()
	{
		SpriteDefinition[] spriteDefinitions = this.loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES);
		this.spriteManager.addSpriteOverrides(spriteDefinitions);

		this.mapComponents = Arrays.asList(mushtreeMap, adventureLogComposite, spiritTreeMap, xericsMap, minecartMap, obeliskMap, skillsNecklaceMap);

		this.adventureLogComposite.addAdventureLogMap(spiritTreeMap);
		this.adventureLogComposite.addAdventureLogMap(xericsMap);
		this.adventureLogComposite.addAdventureLogMap(minecartMap);
		this.adventureLogComposite.addAdventureLogMap(obeliskMap);
		this.adventureLogComposite.addAdventureLogMap(skillsNecklaceMap);

		this.mapComponents.forEach(mapComponent -> eventBus.register(mapComponent));
	}

	@Override
	protected void shutDown()
	{
		this.mapComponents.forEach(mapComponent -> eventBus.unregister(mapComponent));
	}

	public  <T> T loadDefinitionResource(Class<T> classType, String resource)
	{
		// Load the resource as a stream and wrap it in a reader
		InputStream resourceStream = classType.getResourceAsStream(resource);
		InputStreamReader definitionReader = new InputStreamReader(resourceStream);

		// Load the objects from the JSON file
		return gson.fromJson(definitionReader, classType);
	}

	@Provides
	TeleportMapsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TeleportMapsConfig.class);
	}
}
