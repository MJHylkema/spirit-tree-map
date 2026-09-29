package com.mjhylkema.TeleportMaps;

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.mjhylkema.TeleportMaps.components.adventureLog.AdventureLogComposite;
import com.mjhylkema.TeleportMaps.components.IMap;
import com.mjhylkema.TeleportMaps.components.MagicCarpetMap;
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
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;

@Slf4j
@PluginDescriptor(
	name = "Teleport Maps",
	configName = "SpiritTreeMapPlugin", // Original plugin name
	enabledByDefault = true
)
public class TeleportMapsPlugin extends Plugin
{
	private static final String DEF_FILE_SPRITES = "/SpriteDefinitions.json";

	/* Plugin Hub plugin that also replaces the spirit tree menu, so the two can't coexist */
	private static final String SPIRIT_TREE_MENU_PLUGIN = "Spirit Tree Menu";

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
	private PluginManager pluginManager;

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
	private MagicCarpetMap magicCarpetMap;
	@Inject
	AdventureLogComposite adventureLogComposite;

	private List<IMap> mapComponents;

	@Override
	protected void startUp()
	{
		SpriteDefinition[] spriteDefinitions = this.loadDefinitionResource(SpriteDefinition[].class, DEF_FILE_SPRITES);
		this.spriteManager.addSpriteOverrides(spriteDefinitions);
		this.clientThread.invokeLater(() -> SpriteVariants.register(this.client, spriteDefinitions));

		this.mapComponents = Arrays.asList(mushtreeMap, adventureLogComposite, spiritTreeMap, xericsMap, minecartMap, obeliskMap, skillsNecklaceMap, magicCarpetMap);

		this.adventureLogComposite.addAdventureLogMap(spiritTreeMap);
		this.adventureLogComposite.addAdventureLogMap(xericsMap);
		this.adventureLogComposite.addAdventureLogMap(minecartMap);
		this.adventureLogComposite.addAdventureLogMap(obeliskMap);
		this.adventureLogComposite.addAdventureLogMap(skillsNecklaceMap);

		this.mapComponents.forEach(mapComponent -> eventBus.register(mapComponent));
		this.disableSpiritTreeMenuPlugin();
	}

	@Override
	protected void shutDown()
	{
		this.mapComponents.forEach(mapComponent -> eventBus.unregister(mapComponent));
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (e.getGroup().equals(TeleportMapsConfig.GROUP) && e.getKey().equals(TeleportMapsConfig.KEY_SHOW_SPIRIT_TREE_MAP))
			this.disableSpiritTreeMenuPlugin();
	}

	@Subscribe
	public void onPluginChanged(PluginChanged e)
	{
		// Covers the plugin being installed or turned on while the map is enabled
		if (e.isLoaded() && this.isSpiritTreeMenuPlugin(e.getPlugin()))
			this.disableSpiritTreeMenuPlugin();
	}

	/**
	 * Turns off the Spirit Tree Menu plugin while the spirit tree map is
	 * enabled; both build over the same menu and interfere with each other
	 */
	private void disableSpiritTreeMenuPlugin()
	{
		if (!this.config.showSpiritTreeMap())
			return;

		for (Plugin plugin : this.pluginManager.getPlugins())
		{
			if (!this.isSpiritTreeMenuPlugin(plugin) || !this.pluginManager.isPluginEnabled(plugin))
				continue;

			log.info("Disabling the {} plugin as it conflicts with the spirit tree map", SPIRIT_TREE_MENU_PLUGIN);
			this.pluginManager.setPluginEnabled(plugin, false);

			// Plugins may only be stopped from the Swing thread
			SwingUtilities.invokeLater(() ->
			{
				try
				{
					this.pluginManager.stopPlugin(plugin);
				}
				catch (PluginInstantiationException ex)
				{
					log.warn("Failed to stop the {} plugin", SPIRIT_TREE_MENU_PLUGIN, ex);
				}
			});
		}
	}

	private boolean isSpiritTreeMenuPlugin(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		return descriptor != null && descriptor.name().equals(SPIRIT_TREE_MENU_PLUGIN);
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
