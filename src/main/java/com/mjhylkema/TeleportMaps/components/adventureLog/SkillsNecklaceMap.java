package com.mjhylkema.TeleportMaps.components.adventureLog;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.components.BaseMap;
import com.mjhylkema.TeleportMaps.definition.SkillsNecklaceDefinition;
import com.mjhylkema.TeleportMaps.ui.AdventureLogEntry;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SkillsNecklaceMap extends BaseMap implements IAdventureMap {
  /* Definition JSON files */
  private static final String DEF_FILE_SKILLS_NECKLACE = "/SkillsNecklaceMap/SkillsNecklaceDefinitions.json";

  /* Sprite IDs, dimensions and positions */
  private static final int MAP_SPRITE_ID = -19700;
  private static final int MAP_SPRITE_WIDTH = 507;
  private static final int MAP_SPRITE_HEIGHT = 319;
  private static final int SCRIPT_TRIGGER_KEY = 1437;
  private static final String LABEL_NAME_PATTERN = "<col=735a28>(.+)</col>: (<col=5f5f5f>)?(.+)";
  private static final String TRAVEL_ACTION = "Travel";
  private static final String EXAMINE_ACTION = "Examine";
  private static final int ADVENTURE_LOG_CONTAINER_BACKGROUND = 0;
  private static final int ADVENTURE_LOG_CONTAINER_TITLE = 1;

  // There has got to be a better way to do this. All other jewelry seems to use the chat window.
  // But they all say this same phrase. Lucky that skills necklace uses the Adventure Log.
  private static final String MENU_TITLE = "Where would you like to teleport to?";

  private SkillsNecklaceDefinition[] skillsNecklaceDefinitions;
  private HashMap<String, SkillsNecklaceDefinition> skillsNecklaceDefinitionLookup;
  private HashMap<String, AdventureLogEntry<SkillsNecklaceDefinition>> availableLocations;

  @Inject
  public SkillsNecklaceMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread) {
    super(plugin, config, client, clientThread, true);

    this.loadDefinitions();
    this.buildSkillsNecklaceDefinitionLookup();
  }

  @Override
  public boolean matchesTitle(String title) {
    return title.equals(MENU_TITLE); // Regex matches fails because title string contains ? character
  }

  private void loadDefinitions() {
    this.skillsNecklaceDefinitions = this.plugin.loadDefinitionResource(SkillsNecklaceDefinition[].class, DEF_FILE_SKILLS_NECKLACE);
  }

  private void buildSkillsNecklaceDefinitionLookup() {
    this.skillsNecklaceDefinitionLookup = new HashMap<>();
    for (SkillsNecklaceDefinition skillsNecklaceDefinition : this.skillsNecklaceDefinitions) {
      // Place the skills necklace definition in the lookup table indexed by its name
      this.skillsNecklaceDefinitionLookup.put(skillsNecklaceDefinition.getName(), skillsNecklaceDefinition);
    }
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged e)
  {
    switch (e.getKey())
    {
      case TeleportMapsConfig.KEY_SHOW_SKILLS_NECKLACE_MAP:
        this.setActive(config.showSkillsNecklaceMap());
      case TeleportMapsConfig.KEY_DISPLAY_HOTKEYS:
        this.updateTeleports((teleport) -> {
          teleport.setHotKeyVisibility(config.displayHotkeys());
        });
      default:
        super.onConfigChanged(e);
    }
  }

  @Override
  public void buildInterface(Widget adventureLogContainer) {
    this.hideAdventureLogContainerChildren(adventureLogContainer);
    this.buildAvailableTeleportList();

    this.createMapWidget(adventureLogContainer);
    this.createTeleportWidgets(adventureLogContainer);
  }

  private void hideAdventureLogContainerChildren(Widget adventureLogContainer) {
    Widget title = adventureLogContainer.getChild(ADVENTURE_LOG_CONTAINER_TITLE);
    if (title != null)
      title.setHidden(true);
  }

  /**
   * NOTE: Not even sure if this is needed. Needs confirmation if Zeah needs to be unlocked first.
   * Farming and Woodcutting might be blocked like Windertodt on a games necklace.
   */
  private void buildAvailableTeleportList() {
    this.availableLocations = new HashMap<>();

    // Compile the pattern that will match the teleport label
    // and place the hotkey and teleport name into groups
    Pattern labelPattern = Pattern.compile(LABEL_NAME_PATTERN);

    // Get the parent widgets containing the teleport locations list
    Widget teleportList = this.client.getWidget(InterfaceID.ADVENTURE_LOG, 3);

    // Fetch all teleport label widgets
    Widget[] labelWidgets = teleportList.getDynamicChildren();

    for (Widget child : labelWidgets) {
      String shortcutKey;
      String disabledColor;
      String teleportName;

      // Create a pattern matcher with the widgets text content
      Matcher matcher = labelPattern.matcher(child.getText());

      // If the text doesn't match the pattern, skip onto the next
      if (!matcher.matches())
        continue;

      // Extract the pertinent information
      shortcutKey = matcher.group(1);
      disabledColor = matcher.group(2);
      teleportName = matcher.group(3);

      // Don't include unavailable teleports in available collection..
      if (disabledColor != null)
        continue;

      SkillsNecklaceDefinition skillsNecklaceDefinition = this.skillsNecklaceDefinitionLookup.get(teleportName);

      if (skillsNecklaceDefinition == null)
        continue;

      this.availableLocations.put(teleportName, new AdventureLogEntry<>(skillsNecklaceDefinition, child, shortcutKey));
    }
  }

  private void createMapWidget(Widget container)
  {
    this.createSpriteWidget(container,
      MAP_SPRITE_WIDTH,
      MAP_SPRITE_HEIGHT,
      4,
      12,
      MAP_SPRITE_ID);
  }

  private void createTeleportWidgets(Widget container)
  {
    this.clearTeleports();

    for (SkillsNecklaceDefinition skillsNecklaceDefinition : this.skillsNecklaceDefinitions)
    {
      Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
      Widget teleportWidget = container.createChild(-1, WidgetType.GRAPHIC);

      UITeleport teleport = new UITeleport(widgetContainer, teleportWidget);
      teleport.setTeleportSprites(skillsNecklaceDefinition.getSpriteEnabled(), skillsNecklaceDefinition.getSpriteHover(), skillsNecklaceDefinition.getSpriteDisabled());

      teleport.setPosition(skillsNecklaceDefinition.getX(), skillsNecklaceDefinition.getY());
      teleport.setSize(skillsNecklaceDefinition.getWidth(), skillsNecklaceDefinition.getHeight());
      teleport.setName(skillsNecklaceDefinition.getName());

      if (this.isLocationUnlocked(skillsNecklaceDefinition.getName()))
      {
        AdventureLogEntry<SkillsNecklaceDefinition> adventureLogEntry = this.availableLocations.get(skillsNecklaceDefinition.getName());

        teleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(adventureLogEntry));

        UIHotkey hotkey = this.createHotKey(container, skillsNecklaceDefinition.getHotkey(), adventureLogEntry.getKeyShortcut());
        teleport.attachHotkey(hotkey);
      }
      else
      {
        teleport.setLocked(true);
        teleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(skillsNecklaceDefinition));
      }

      this.addTeleport(teleport);
    }

  }
  private boolean isLocationUnlocked(String teleportName)
  {
    return this.availableLocations.containsKey(teleportName);
  }

  private void triggerLockedMessage(SkillsNecklaceDefinition skillsNecklaceDefinition)
  {
    this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("You are unable to travel to %s.", skillsNecklaceDefinition.getName()), null));
  }

  private void triggerTeleport(AdventureLogEntry<SkillsNecklaceDefinition> adventureLogEntry)
  {
    this.clientThread.invokeLater(() -> this.client.runScript(SCRIPT_TRIGGER_KEY, this.client.getWidget(0xBB0003).getId(), adventureLogEntry.getWidget().getIndex()));
  }
}