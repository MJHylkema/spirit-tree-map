package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.HotKeyDefinition;
import com.mjhylkema.TeleportMaps.definition.NecklaceOfPassageDefinition;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UIHotkey;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;

import javax.inject.Inject;
import java.awt.*;
import java.util.*;
import java.util.List;

@Slf4j
public class NecklaceOfPassageMap extends BaseMap {
  /* Definition JSON files */
  private static final String DEF_FILE_NECKLACE_OF_PASSAGE = "/NecklaceOfPassageMap/NecklaceOfPassageDefinitions.json";

  /* Item IDSs */
  private static final Set<Integer> NECKLACE_OF_PASSAGE_IDS = Set.of(21155, 21153, 21151, 21149, 21146);
  private static final int INTERACTION_OP_ID = 4;

  /* Sprite IDs, dimensions and positions */
  private static final int MAP_SPRITE_ID = -19900;
  private static final int MAP_SPRITE_WIDTH = 512;
  private static final int MAP_SPRITE_HEIGHT = 334;
  private static final int GEM_SPRITE_ID = -19901;
  private static final int GEM_HIGHLIGHTED_SPRITE_ID = -19902;
  private static final int GEM_SELECTED_SPRITE_ID = -19903;
  private static final int GEM_DISABLED_SPRITE_ID = -19904;
  private static final int CLOSE_BUTTON_SPRITE_ID = 537;
  private static final int CLOSE_BUTTON_WIDTH = 26;
  private static final int CLOSE_BUTTON_HEIGHT = 23;
  private static final int CLOSE_BUTTON_X = 427;
  private static final int CLOSE_BUTTON_Y = 24;
  private static final int DIALOG_OPTION_GROUP_ID = 219;
  private static final int DIALOG_OPTION_CONTAINER_CHILD = 1;
  private static final String DECLINE_OPTION = "Cancel";
  private static final String TRAVEL_ACTION = "Travel";
  private static final String EXAMINE_ACTION = "Examine";

  /* The keyboard digit for the first dialog option; options natively respond
   to the number keys matching their position in the dialog */
  private static final int OPTION_KEY_CHAR_BASE = '0';

  private NecklaceOfPassageDefinition[] destinationDefinitions;

  /* Widgets built on the top-level interface, removed when the dialog closes */
  private boolean interactedWith = false;
  final private List<Widget> screenWidgets = new ArrayList<>();

  /**
   * A dialog option present in the currently open "Select an option" menu
   */
  private static class DialogOption {
    final Widget widget;
    final int childIndex;
    final String text;

    DialogOption(Widget widget, int childIndex, String text) {
      this.widget = widget;
      this.childIndex = childIndex;
      this.text = text;
    }
  }

  @Inject
  public NecklaceOfPassageMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread) {
    super(plugin, config, client, clientThread, config.showNecklaceOfPassageMap());
    this.loadDefinitions();
  }

  private void loadDefinitions()
  {
    this.destinationDefinitions = this.plugin.loadDefinitionResource(NecklaceOfPassageDefinition[].class, DEF_FILE_NECKLACE_OF_PASSAGE);
  }

  @Subscribe
  public void onMenuOptionClicked(MenuOptionClicked e) {
    this.interactedWith = false;

    if (!this.isActive())
      return;

    if (!NECKLACE_OF_PASSAGE_IDS.contains(e.getItemId()))
      return;

    if (e.getItemOp() != INTERACTION_OP_ID)
      return;

    this.interactedWith = true;
  }

  @Subscribe
  public void onWidgetLoaded(WidgetLoaded e) {
    if (!this.isActive())
      return;

    if (e.getGroupId() != DIALOG_OPTION_GROUP_ID)
      return;

    if (!interactedWith)
      return;

    // The dialog options are populated after the interface loads,
    // so inspect the dialog on the next client cycle
    this.clientThread.invokeLater(this::tryBuildInterface);
    interactedWith = false;
  }

  @Subscribe
  public void onWidgetClosed(WidgetClosed e) {
    if (e.getGroupId() == DIALOG_OPTION_GROUP_ID)
      this.destroyInterface();
  }

  @Subscribe
  public void onConfigChanged(ConfigChanged e) {
    switch (e.getKey()) {
      case TeleportMapsConfig.KEY_SHOW_NECKLACE_OF_PASSAGE_MAP:
        this.setActive(config.showNecklaceOfPassageMap());
      default:
        super.onConfigChanged(e);
    }
  }

  private void tryBuildInterface() {
    Widget container = this.client.getWidget(DIALOG_OPTION_GROUP_ID, DIALOG_OPTION_CONTAINER_CHILD);
    if (container == null)
      return;

    Map<String, NecklaceOfPassageMap.DialogOption> options = this.parseDialogOptions(container);
    if (options.isEmpty())
      return;

    this.buildInterface(options);
  }

  /**
   * Collects the option entries from the chat options dialog. Only the
   * selectable options carry key listeners; the title widget does not,
   * so it's naturally excluded.
   */
  private Map<String, DialogOption> parseDialogOptions(Widget container) {
    Map<String, NecklaceOfPassageMap.DialogOption> options = new HashMap<>();

    Widget[] children = container.getDynamicChildren();

    for (int i = 0; i < children.length; i++) {
      Widget child = children[i];
      String text = child.getText();

      if (text == null || text.isEmpty() || child.getOnKeyListener() == null)
        continue;

      options.put(text, new NecklaceOfPassageMap.DialogOption(child, i, text));
    }

    return options;
  }

  private void buildInterface(Map<String, NecklaceOfPassageMap.DialogOption> options) {
    // The chat dialog is too small to hold the map (its ancestor layers
    // clip to the chatbox), so the map is built over the game view
    // instead, centered in the same modal layer the game opens
    // screen-level interfaces such as the adventure log into. The
    // scroll backdrop is part of the map sprite.
    Widget screen = this.getScreenContainer();
    if (screen == null)
      return;

    int mapX = (screen.getWidth() - MAP_SPRITE_WIDTH) / 2;
    int mapY = Math.max(0, (screen.getHeight() - MAP_SPRITE_HEIGHT) / 2);

    this.screenWidgets.clear();

    Widget map = this.createSpriteWidget(screen, MAP_SPRITE_WIDTH, MAP_SPRITE_HEIGHT, mapX, mapY, MAP_SPRITE_ID);
    this.trackScreenWidget(map);

    // Set hasListener / noClickThrough to disallow click-through
    map.setHasListener(true);
    map.setNoClickThrough(true);

    this.createTeleportWidgets(screen, options, mapX, mapY);
    this.createCloseButton(screen, options, mapX, mapY);
  }

  /**
   * Finds the "mainmodal" layer for the current display mode: the layer
   * over the game view that the game opens screen-level modals into
   */
  private Widget getScreenContainer() {
    int[][] containers = {
      {161, 16}, // toplevel_osrs_stretch:mainmodal (resizable classic)
      {164, 16}, // toplevel_pre_eoc:mainmodal (resizable modern)
      {548, 41}, // toplevel:mainmodal (fixed)
    };

    for (int[] componentId : containers) {
      Widget screen = this.client.getWidget(componentId[0], componentId[1]);
      if (screen != null)
        return screen;
    }

    return null;
  }

  private void trackScreenWidget(Widget widget)
  {
    this.screenWidgets.add(widget);
  }

  /**
   * Variant of {@link BaseMap#createHotKey} that positions the hotkey
   * relative to the map and tracks its widgets for later removal
   */
  private UIHotkey createScreenHotKey(Widget screen, HotKeyDefinition hotKeyDefinition, String label, int mapX, int mapY)
  {
    Widget icon = screen.createChild(-1, WidgetType.GRAPHIC);
    icon.setSpriteId(HOTKEY_LABEL_SPRITE_ID);
    Widget text = screen.createChild(-1, WidgetType.TEXT);
    this.trackScreenWidget(icon);
    this.trackScreenWidget(text);

    UIHotkey hotkey = new UIHotkey(icon, text);
    hotkey.setSize(hotKeyDefinition.getWidth(), hotKeyDefinition.getHeight());
    hotkey.setPosition(mapX + hotKeyDefinition.getX(), mapY + hotKeyDefinition.getY());
    hotkey.setText(label);
    hotkey.setVisibility(this.config.displayHotkeys());

    return hotkey;
  }

  /**
   * Hides the widgets built on the top-level interface. Unlike the other
   * maps, these aren't children of the travel dialog, so they outlive it
   * and must be removed when the dialog closes.
   */
  private void destroyInterface()
  {
    if (this.screenWidgets.isEmpty())
      return;

    this.screenWidgets.clear();
    Objects.requireNonNull(getScreenContainer()).deleteAllChildren();
    this.clearTeleports();
  }

  private void createTeleportWidgets(Widget container, Map<String, NecklaceOfPassageMap.DialogOption> options, int mapX, int mapY)
  {
    this.clearTeleports();

    for (NecklaceOfPassageDefinition definition : this.destinationDefinitions)
    {
      Widget widgetContainer = container.createChild(-1, WidgetType.GRAPHIC);
      Widget gemWidget = container.createChild(-1, WidgetType.GRAPHIC);
      this.trackScreenWidget(widgetContainer);
      this.trackScreenWidget(gemWidget);

      UITeleport gemTeleport = new UITeleport(widgetContainer, gemWidget);

      gemTeleport.setPosition(mapX + definition.getX(), mapY + definition.getY());
      gemTeleport.setSize(NecklaceOfPassageDefinition.getWidth(), NecklaceOfPassageDefinition.getHeight());
      gemTeleport.setName(definition.getName());


      if (options.containsKey(definition.getName())) {
        NecklaceOfPassageMap.DialogOption destinationOption = options.get(definition.getName());
        gemTeleport.setTeleportSprites(GEM_SPRITE_ID, GEM_HIGHLIGHTED_SPRITE_ID, GEM_DISABLED_SPRITE_ID);
        gemTeleport.addAction(TRAVEL_ACTION, () -> this.triggerTravel(destinationOption));

        gemTeleport.addOnHoverListener((listener) -> {
          destinationOption.widget.setTextColor(Color.WHITE.getRGB());
        });
        gemTeleport.addOnLeaveListener((listener) -> {
          destinationOption.widget.setTextColor(Color.BLACK.getRGB());
        });

        // The dialog options natively respond to their number key;
        // child index lines up with the displayed option number
        int hotkeyDigit = destinationOption.childIndex;
        if (hotkeyDigit >= 1 && hotkeyDigit <= 9)
        {
          UIHotkey hotkey = this.createScreenHotKey(container, definition.getHotkey(), String.valueOf(hotkeyDigit), mapX, mapY);
          gemTeleport.attachHotkey(hotkey);
        }
      }
      else
      {
        gemTeleport.setTeleportSprites(GEM_SPRITE_ID, GEM_HIGHLIGHTED_SPRITE_ID, GEM_DISABLED_SPRITE_ID);
        gemTeleport.setLocked(true);
        gemTeleport.addAction(EXAMINE_ACTION, () -> this.triggerLockedMessage(definition));
      }

      this.addTeleport(gemTeleport);
    }
  }

  private void createCloseButton(Widget container, Map<String, NecklaceOfPassageMap.DialogOption> options, int mapX, int mapY)
  {
    if (!options.containsKey(DECLINE_OPTION))
      return;
    final NecklaceOfPassageMap.DialogOption  decline = options.get(DECLINE_OPTION);

    Widget closeWidget = container.createChild(-1, WidgetType.GRAPHIC);
    this.trackScreenWidget(closeWidget);
    UIButton closeButton = new UIButton(closeWidget);
    closeButton.setPosition(mapX + CLOSE_BUTTON_X, mapY + CLOSE_BUTTON_Y);
    closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
    closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
    closeButton.addAction("Close", () -> this.triggerTravel(decline));
    closeWidget.revalidate();
  }

  /**
   * Selects the given dialog option by dispatching the key listener the
   * game attached to the option widget, with the event placeholders
   * filled in as if the option's number key had been pressed
   */
  private void triggerTravel(NecklaceOfPassageMap.DialogOption option)
  {
    this.clientThread.invokeLater(() ->
    {
      Object[] template = option.widget.getOnKeyListener();
      if (template == null)
      {
        log.debug("No key listener on dialog option '{}'", option.text);
        return;
      }

      int digit = option.childIndex;
      if (digit < 1 || digit > 9)
        return;

      Object[] listener = new Object[template.length];
      for (int i = 0; i < template.length; i++)
      {
        Object arg = template[i];
        if (arg instanceof Integer)
        {
          switch ((Integer) arg)
          {
            case ScriptEvent.KEY_CODE:
              arg = KeyCode.KC_1 + digit - 1;
              break;
            case ScriptEvent.KEY_CHAR:
              arg = OPTION_KEY_CHAR_BASE + digit;
              break;
            case ScriptEvent.WIDGET_ID:
              arg = option.widget.getId();
              break;
            case ScriptEvent.WIDGET_INDEX:
              arg = option.widget.getIndex();
              break;
            default:
              break;
          }
        }
        listener[i] = arg;
      }

      this.runPacketSendingScript(listener);
      this.destroyInterface();
    });
  }

  private void triggerLockedMessage(NecklaceOfPassageDefinition definition)
  {
    this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format("The necklace does not have the power to take you to %s yet.", definition.getName()), null));
  }
}