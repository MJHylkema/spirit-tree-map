package com.mjhylkema.TeleportMaps.components;

import com.mjhylkema.TeleportMaps.JewelleryBoxLayout;
import com.mjhylkema.TeleportMaps.ScrollMapRenderer;
import com.mjhylkema.TeleportMaps.SpriteVariants;
import com.mjhylkema.TeleportMaps.TeleportMapsConfig;
import com.mjhylkema.TeleportMaps.TeleportMapsPlugin;
import com.mjhylkema.TeleportMaps.definition.JewelleryBoxCategoryDefinition;
import com.mjhylkema.TeleportMaps.definition.JewelleryBoxDefinition;
import com.mjhylkema.TeleportMaps.definition.SkillsNecklaceDefinition;
import com.mjhylkema.TeleportMaps.ui.UIButton;
import com.mjhylkema.TeleportMaps.ui.UITeleport;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Replaces the POH jewellery box with a map of the destinations it offers,
 * each marked with its jewellery's item icon. Either every destination is
 * shown on one world map, or each jewellery type gets a tab with a map
 * cropped to its destinations.
 */
@Slf4j
public class JewelleryBoxMap extends BaseMap
{
	/* Definition JSON files */
	private static final String DEF_FILE_JEWELLERY_BOX = "/JewelleryBoxMap/JewelleryBoxDefinitions.json";
	private static final String DEF_FILE_SKILLS_NECKLACE = "/SkillsNecklaceMap/SkillsNecklaceDefinitions.json";

	/* poh_jewellery_box_init, which builds the box's buttons */
	private static final int JEWELLERY_BOX_INIT_SCRIPT_ID = 1685;
	private static final int JEWELLERY_BOX_GROUP_ID = net.runelite.api.gameval.InterfaceID.POH_JEWELLERY_BOX;
	private static final int JEWELLERY_BOX_UNIVERSE = 0;
	private static final int JEWELLERY_BOX_FRAME = 1;

	/* Matches a jewellery box button label, e.g. "<col=ccccff>1:</col> Emir's Arena".
	   Locked destinations are wrapped in <str> tags. */
	private static final Pattern ENTRY_LABEL_PATTERN = Pattern.compile("<col=ccccff>(.+):</col> (.+)");
	private static final String LOCKED_TAG = "<str>";

	/* The box's content area, inside its frame, as the box lays out its own
	   buttons. The box must keep its size: the game only redraws the area it
	   laid the box out in, so a resized box draws late and leaves stale
	   pixels behind when closed. The maps are scaled to fit instead. */
	private static final int CONTENT_LEFT = 10;
	private static final int CONTENT_TOP = 40;
	private static final int CONTENT_RIGHT = 10;
	private static final int CONTENT_BOTTOM = 10;

	/* The Spirit Tree world map. Destination positions in the definitions are
	   on this full size map. */
	private static final String WORLD_MAP_FILE = "/SpiritTreeMap/SpiritTreeMap.png";

	/* The single map shows the region of the world map holding every destination */
	private static final int SINGLE_MAP_SPRITE_ID = -19990;
	private static final int SINGLE_MAP_CROP_X = 88;
	private static final int SINGLE_MAP_CROP_Y = 38;
	private static final int SINGLE_MAP_CROP_WIDTH = 324;
	private static final int SINGLE_MAP_CROP_HEIGHT = 200;

	/* Each tab draws the region of the world map around its destinations on
	   a scroll, like the Xeric's talisman and Wilderness obelisk maps. The
	   region stays within the world map's solid parchment, clear of its
	   torn edges. */
	private static final int TAB_MAP_SPRITE_BASE_ID = -19980;
	private static final int TAB_MAP_PADDING = 30;
	private static final float TAB_MAP_MAX_ZOOM = 2.0f;
	private static final Rectangle WORLD_MAP_PARCHMENT = new Rectangle(40, 44, 420, 256);

	/* Destination labels in the tabbed layout, placed relative to the marker */
	private static final String LABEL_BELOW = "below";
	private static final String LABEL_ABOVE = "above";
	private static final String LABEL_LEFT = "left";
	private static final String LABEL_RIGHT = "right";
	private static final int LABEL_HEIGHT = 14;
	private static final int LABEL_CHAR_WIDTH = 5;
	private static final int LABEL_PADDING = 4;
	private static final int LABEL_GAP = 2;
	private static final int LABEL_EDGE_MARGIN = 18;
	private static final int LABEL_LOCKED_COLOUR = 0x9f9f9f;

	/* The Skills necklace tab shows the Skills Necklace map. Its definitions
	   position the guilds relative to where that map is drawn in the
	   adventure log. */
	private static final String SKILLS_MAP_FILE = "/SkillsNecklaceMap/SkillsNecklaceMap.png";
	private static final int SKILLS_MAP_WIDTH = 507;
	private static final int SKILLS_MAP_HEIGHT = 317;
	private static final int SKILLS_MAP_X = 4;
	private static final int SKILLS_MAP_Y = 13;

	/* The tabbed layout's scroll, at the proportions of the scroll maps */
	private static final int SCROLL_WIDTH = 509;
	private static final int SCROLL_HEIGHT = 317;

	/* The tab strip, along the top of the scroll's parchment. Tabs have no
	   background, bar a highlight behind the open tab and a hovered tab. */
	private static final int TAB_HEIGHT = 24;
	private static final int TAB_GAP = 3;
	private static final int TAB_MAP_GAP = 2;
	private static final int TAB_ICON_WIDTH = 24;
	private static final int TAB_ICON_HEIGHT = 21;
	private static final int TAB_ICON_PADDING = 3;
	private static final int TAB_COLOUR = 0x3e3529;
	private static final int TAB_SELECTED_OPACITY = 140;
	private static final int TAB_HOVER_OPACITY = 200;
	private static final int TAB_OPACITY = 255;
	private static final int TAB_TEXT_COLOUR = 0xff981f;
	private static final int TAB_SELECTED_TEXT_COLOUR = 0xffffff;
	private static final int TAB_DISABLED_TEXT_COLOUR = 0x7f7f7f;

	/* The close button, in the scroll's top right corner, replacing the
	   box's own, which is hidden with its frame */
	private static final int CLOSE_BUTTON_SPRITE_ID = 537;
	private static final int CLOSE_BUTTON_WIDTH = 26;
	private static final int CLOSE_BUTTON_HEIGHT = 23;
	private static final int CLOSE_BUTTON_MARGIN = 2;
	private static final int IF_CLOSE_SCRIPT_ID = 29;

	/* Sprites generated at runtime from each jewellery type's item icon. Each
	   jewellery type gets a block of ids below this base. */
	private static final int ITEM_SPRITE_BASE_ID = -19900;
	private static final int ITEM_SPRITE_BLOCK = 10;
	private static final int MARKER_SPRITE = 0;
	private static final int MARKER_HOVER_SPRITE = 1;
	private static final int MARKER_DISABLED_SPRITE = 2;
	private static final int TAB_ICON_SPRITE = 3;
	private static final int TAB_ICON_DISABLED_SPRITE = 4;

	/* The destination name shown while a marker is hovered */
	private static final int TOOLTIP_HEIGHT = 16;
	private static final int TOOLTIP_CHAR_WIDTH = 6;
	private static final int TOOLTIP_PADDING = 8;
	private static final int TOOLTIP_GAP = 2;
	private static final int TOOLTIP_COLOUR = 0x3e3529;
	private static final int TOOLTIP_OPACITY = 40;

	private static final String TRAVEL_ACTION = "Teleport";
	private static final String VIEW_ACTION = "View";
	private static final String EXAMINE_ACTION = "Examine";

	private final ItemManager itemManager;

	private final JewelleryBoxCategoryDefinition[] categoryDefinitions;
	private final SkillsNecklaceDefinition[] skillsNecklaceDefinitions;

	/* The jewellery box buttons, indexed by destination name */
	private HashMap<String, BoxEntry> availableEntries;
	private boolean itemSpritesRequested;
	/* What each map sprite was last generated from, and at what size */
	private final HashMap<Integer, String> mapSpriteKeys = new HashMap<>();
	private final HashMap<String, BufferedImage> loadedImages = new HashMap<>();

	private Widget tooltipBackground;
	private Widget tooltipText;

	/* The tabbed layout's tabs and the widgets shown on each */
	private List<Tab> tabs;
	private int selectedTab;

	/**
	 * A teleport button in the open jewellery box
	 */
	private static class BoxEntry
	{
		final Widget widget;
		final String keyShortcut;
		final boolean locked;

		BoxEntry(Widget widget, String keyShortcut, boolean locked)
		{
			this.widget = widget;
			this.keyShortcut = keyShortcut;
			this.locked = locked;
		}
	}

	/**
	 * A map image placed in the box, showing a region of a source image
	 */
	private static class MapView
	{
		/* The map image's bounds in the box */
		final int x;
		final int y;
		final int width;
		final int height;
		/* Where the region's top left corner is drawn in the box, and its scale */
		final int originX;
		final int originY;
		final float cropX;
		final float cropY;
		final float scale;
		/* The area labels are kept within, in the box */
		Rectangle labelArea;

		MapView(int x, int y, int width, int height, int originX, int originY, float cropX, float cropY, float scale)
		{
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.originX = originX;
			this.originY = originY;
			this.cropX = cropX;
			this.cropY = cropY;
			this.scale = scale;
			this.labelArea = new Rectangle(x + LABEL_EDGE_MARGIN, y, width - LABEL_EDGE_MARGIN * 2, height);
		}

		/**
		 * The region scaled to fit the area, centred, with the image showing only the region
		 */
		static MapView fit(int areaX, int areaY, int areaWidth, int areaHeight, float cropX, float cropY, float cropWidth, float cropHeight)
		{
			float scale = Math.min(areaWidth / cropWidth, areaHeight / cropHeight);
			int width = Math.round(cropWidth * scale);
			int height = Math.round(cropHeight * scale);
			int x = areaX + (areaWidth - width) / 2;
			int y = areaY + (areaHeight - height) / 2;
			return new MapView(x, y, width, height, x, y, cropX, cropY, scale);
		}

		/* Converts a position on the source image to the box */
		int toBoxX(float x)
		{
			return this.originX + Math.round((x - this.cropX) * this.scale);
		}

		int toBoxY(float y)
		{
			return this.originY + Math.round((y - this.cropY) * this.scale);
		}
	}

	/**
	 * A tab in the tabbed layout
	 */
	private static class Tab
	{
		final JewelleryBoxCategoryDefinition category;
		final boolean available;
		final List<Widget> content = new ArrayList<>();
		Widget background;
		Widget icon;
		Widget label;

		Tab(JewelleryBoxCategoryDefinition category, boolean available)
		{
			this.category = category;
			this.available = available;
		}
	}

	@Inject
	public JewelleryBoxMap(TeleportMapsPlugin plugin, TeleportMapsConfig config, Client client, ClientThread clientThread, ItemManager itemManager)
	{
		super(plugin, config, client, clientThread, config.showJewelleryBoxMap());
		this.itemManager = itemManager;
		this.categoryDefinitions = this.plugin.loadDefinitionResource(JewelleryBoxCategoryDefinition[].class, DEF_FILE_JEWELLERY_BOX);
		this.skillsNecklaceDefinitions = this.plugin.loadDefinitionResource(SkillsNecklaceDefinition[].class, DEF_FILE_SKILLS_NECKLACE);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired e)
	{
		if (e.getScriptId() != JEWELLERY_BOX_INIT_SCRIPT_ID || !this.isActive())
			return;

		// Hide the box upfront so it can't flash on screen. The init script runs
		// while the box is opening, before the box has been laid out, and
		// widgets added at that point aren't drawn until the whole interface
		// is next laid out (e.g. on a window resize), so build the map once
		// the box is on screen.
		this.hideJewelleryBox();
		this.clientThread.invokeLater(this::buildInterface);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		switch (e.getKey())
		{
			case TeleportMapsConfig.KEY_SHOW_JEWELLERY_BOX_MAP:
				this.setActive(config.showJewelleryBoxMap());
			default:
				super.onConfigChanged(e);
		}
	}

	/**
	 * Builds the map inside the box's own root layer. The box is a modal, and
	 * the game draws it above anything added to the layer it opens in, so
	 * the map must live inside the box to receive clicks. The init script
	 * clears this layer whenever it runs, removing any previous map.
	 */
	private void buildInterface()
	{
		Widget universe = this.client.getWidget(JEWELLERY_BOX_GROUP_ID, JEWELLERY_BOX_UNIVERSE);
		if (universe == null)
			return;

		this.requestItemSprites();
		this.buildAvailableEntryList();
		this.hideJewelleryBox();
		this.clearTeleports();

		if (config.jewelleryBoxLayout() == JewelleryBoxLayout.TABS)
		{
			this.buildTabbedLayout(universe, universe.getWidth(), universe.getHeight());
		}
		else
		{
			int contentWidth = universe.getWidth() - CONTENT_LEFT - CONTENT_RIGHT;
			int contentHeight = universe.getHeight() - CONTENT_TOP - CONTENT_BOTTOM;
			this.buildSingleMapLayout(universe, contentWidth, contentHeight);
		}

		// Created last so it's drawn above the markers
		this.createTooltip(universe);
	}

	/**
	 * Hides the box's buttons. The single map keeps the box's frame (with its
	 * title and close button) around it; the tabbed layout's scroll replaces
	 * the frame. The box's root layer stays visible, as it holds the key
	 * listeners that make the native hotkeys work.
	 */
	private void hideJewelleryBox()
	{
		Widget frame = this.client.getWidget(JEWELLERY_BOX_GROUP_ID, JEWELLERY_BOX_FRAME);
		if (frame != null)
			frame.setHidden(config.jewelleryBoxLayout() == JewelleryBoxLayout.TABS);

		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
		{
			Widget layer = this.client.getWidget(JEWELLERY_BOX_GROUP_ID, category.getChildId());
			if (layer != null)
				layer.setHidden(true);
		}
	}

	/**
	 * Collects the teleport buttons the jewellery box created. The box only
	 * creates buttons for the jewellery types its tier can hold.
	 */
	private void buildAvailableEntryList()
	{
		this.availableEntries = new HashMap<>();

		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
		{
			Widget layer = this.client.getWidget(JEWELLERY_BOX_GROUP_ID, category.getChildId());
			if (layer == null || layer.getDynamicChildren() == null)
				continue;

			for (Widget child : layer.getDynamicChildren())
			{
				if (child.getText() == null)
					continue;

				Matcher matcher = ENTRY_LABEL_PATTERN.matcher(child.getText());
				if (!matcher.matches())
					continue;

				String shortcutKey = matcher.group(1);
				String label = matcher.group(2);
				String teleportName = Text.removeTags(label);

				this.availableEntries.put(teleportName, new BoxEntry(child, shortcutKey, label.contains(LOCKED_TAG)));
			}
		}
	}

	private boolean isCategoryAvailable(JewelleryBoxCategoryDefinition category)
	{
		for (JewelleryBoxDefinition destination : category.getDestinations())
		{
			if (this.availableEntries.containsKey(destination.getName()))
				return true;
		}
		return false;
	}

	private void buildSingleMapLayout(Widget container, int contentWidth, int contentHeight)
	{
		MapView view = MapView.fit(CONTENT_LEFT, CONTENT_TOP, contentWidth, contentHeight,
			SINGLE_MAP_CROP_X, SINGLE_MAP_CROP_Y, SINGLE_MAP_CROP_WIDTH, SINGLE_MAP_CROP_HEIGHT);
		this.createMapWidget(container, view, SINGLE_MAP_SPRITE_ID, "single",
			() -> this.cropAndScale(WORLD_MAP_FILE, view, SINGLE_MAP_CROP_WIDTH, SINGLE_MAP_CROP_HEIGHT));

		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
			this.createWorldMapMarkers(container, view, category, false);
	}

	/**
	 * Lays out the tabbed layout on a scroll filling the box's width, in
	 * place of the box's frame: the tabs along the top of the parchment, the
	 * map below them, and a close button in the corner
	 */
	private void buildTabbedLayout(Widget container, int boxWidth, int boxHeight)
	{
		this.tabs = new ArrayList<>();
		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
			this.tabs.add(new Tab(category, this.isCategoryAvailable(category)));

		// Reopen the last tab, unless this box can't hold that jewellery
		if (!this.tabs.get(this.selectedTab).available)
		{
			for (int i = 0; i < this.tabs.size(); i++)
			{
				if (this.tabs.get(i).available)
				{
					this.selectedTab = i;
					break;
				}
			}
		}

		// The scroll, as wide as the box allows at the scroll's proportions
		int scrollWidth = boxWidth;
		int scrollHeight = Math.round(boxWidth * (float) SCROLL_HEIGHT / SCROLL_WIDTH);
		if (scrollHeight > boxHeight)
		{
			scrollHeight = boxHeight;
			scrollWidth = Math.round(boxHeight * (float) SCROLL_WIDTH / SCROLL_HEIGHT);
		}
		Rectangle scroll = new Rectangle((boxWidth - scrollWidth) / 2, (boxHeight - scrollHeight) / 2, scrollWidth, scrollHeight);

		// Within the scroll: the parchment between its rolled sides and torn
		// edges, holding the tabs, then the map below them
		Rectangle inner = ScrollMapRenderer.innerBounds(scrollWidth, scrollHeight);
		int closeX = scroll.x + scroll.width - CLOSE_BUTTON_WIDTH - CLOSE_BUTTON_MARGIN;
		int closeY = scroll.y + CLOSE_BUTTON_MARGIN;
		Rectangle tabStrip = new Rectangle(scroll.x + inner.x, scroll.y + inner.y, closeX - TAB_GAP - (scroll.x + inner.x), TAB_HEIGHT);
		int mapTop = inner.y + TAB_HEIGHT + TAB_MAP_GAP;
		// The map runs to the scroll's bottom, clipped by its torn edge, but
		// labels stay above the torn edge
		Rectangle mapArea = new Rectangle(inner.x, mapTop, inner.width, scrollHeight - mapTop);
		Rectangle labelArea = new Rectangle(scroll.x + inner.x, scroll.y + mapTop, inner.width, inner.y + inner.height - mapTop);

		// Every tab's map is built upfront, so switching tabs only changes
		// which widgets are shown
		for (int i = 0; i < this.tabs.size(); i++)
		{
			Tab tab = this.tabs.get(i);
			int firstChild = this.countDynamicChildren(container);

			if (tab.category.usesSkillsNecklaceMap())
				this.buildSkillsNecklaceTab(container, scroll, labelArea);
			else
				this.buildWorldMapTab(container, tab.category, TAB_MAP_SPRITE_BASE_ID - i, scroll, mapArea, labelArea);

			Widget[] children = container.getDynamicChildren();
			for (int child = firstChild; child < children.length; child++)
				tab.content.add(children[child]);

			this.setTabContentVisible(tab, i == this.selectedTab);
		}

		this.createTabStrip(container, tabStrip);
		this.createCloseButton(container, closeX, closeY);
	}

	private int countDynamicChildren(Widget container)
	{
		Widget[] children = container.getDynamicChildren();
		return children == null ? 0 : children.length;
	}

	/**
	 * A tab showing its destinations on a scroll, drawn from the region of
	 * the world map around them
	 * @param scroll the scroll's bounds in the box
	 * @param mapArea where the map is drawn, within the scroll
	 * @param labelArea the area labels are kept within, in the box
	 */
	private void buildWorldMapTab(Widget container, JewelleryBoxCategoryDefinition category, int spriteId,
		Rectangle scroll, Rectangle mapArea, Rectangle labelArea)
	{
		JewelleryBoxDefinition[] destinations = category.getDestinations();
		int[] xs = new int[destinations.length];
		int[] ys = new int[destinations.length];
		for (int i = 0; i < destinations.length; i++)
		{
			xs[i] = destinations[i].getX();
			ys[i] = destinations[i].getY();
		}

		float[] crop = ScrollMapRenderer.cropAround(xs, ys, (float) mapArea.width / mapArea.height, TAB_MAP_PADDING,
			mapArea.width / TAB_MAP_MAX_ZOOM, WORLD_MAP_PARCHMENT);

		MapView view = new MapView(scroll.x, scroll.y, scroll.width, scroll.height, scroll.x + mapArea.x, scroll.y + mapArea.y,
			crop[0], crop[1], mapArea.width / crop[2]);
		view.labelArea = labelArea;
		this.createMapWidget(container, view, spriteId, Arrays.toString(crop) + mapArea, () -> ScrollMapRenderer.render(
			this.loadImage(ScrollMapRenderer.TEMPLATE_FILE), this.loadImage(WORLD_MAP_FILE),
			crop[0], crop[1], crop[2], crop[3], scroll.width, scroll.height, mapArea));
		this.createWorldMapMarkers(container, view, category, true);
	}

	/**
	 * The Skills necklace tab, on the Skills Necklace map's own scroll
	 */
	private void buildSkillsNecklaceTab(Widget container, Rectangle scroll, Rectangle labelArea)
	{
		JewelleryBoxCategoryDefinition category = this.findSkillsCategory();
		int spriteId = TAB_MAP_SPRITE_BASE_ID - this.indexOf(category);
		MapView view = MapView.fit(scroll.x, scroll.y, scroll.width, scroll.height, 0, 0, SKILLS_MAP_WIDTH, SKILLS_MAP_HEIGHT);
		view.labelArea = labelArea;
		this.createMapWidget(container, view, spriteId, "skills",
			() -> this.cropAndScale(SKILLS_MAP_FILE, view, SKILLS_MAP_WIDTH, SKILLS_MAP_HEIGHT));

		for (SkillsNecklaceDefinition definition : this.skillsNecklaceDefinitions)
		{
			// The guild art keeps its size, so place it by its centre
			float centreX = definition.getX() - SKILLS_MAP_X + definition.getWidth() / 2f;
			float centreY = definition.getY() - SKILLS_MAP_Y + definition.getHeight() / 2f;

			this.createMarker(container, view, category, definition.getName(),
				view.toBoxX(centreX) - definition.getWidth() / 2, view.toBoxY(centreY) - definition.getHeight() / 2,
				definition.getWidth(), definition.getHeight(),
				definition.getSpriteEnabled(), definition.getSpriteHover(), definition.getSpriteDisabled(),
				this.findLabelPlacement(definition.getName()));
		}
	}

	private void createCloseButton(Widget container, int x, int y)
	{
		Widget closeWidget = container.createChild(-1, WidgetType.GRAPHIC);
		UIButton closeButton = new UIButton(closeWidget);
		closeButton.setPosition(x, y);
		closeButton.setSize(CLOSE_BUTTON_WIDTH, CLOSE_BUTTON_HEIGHT);
		closeButton.setSprites(CLOSE_BUTTON_SPRITE_ID, CLOSE_BUTTON_SPRITE_ID);
		closeButton.addAction("Close", () -> this.clientThread.invokeLater(() -> this.client.runScript(IF_CLOSE_SCRIPT_ID)));
		closeWidget.revalidate();
	}

	/**
	 * The label placement given for a destination in the jewellery box definitions
	 */
	private String findLabelPlacement(String name)
	{
		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
		{
			for (JewelleryBoxDefinition destination : category.getDestinations())
			{
				if (destination.getName().equals(name))
					return destination.getLabel() == null ? LABEL_BELOW : destination.getLabel();
			}
		}
		return LABEL_BELOW;
	}

	private BufferedImage cropAndScale(String file, MapView view, float cropWidth, float cropHeight)
	{
		BufferedImage source = this.loadImage(file);
		BufferedImage crop = source.getSubimage(Math.round(view.cropX), Math.round(view.cropY),
			Math.min(Math.round(cropWidth), source.getWidth() - Math.round(view.cropX)),
			Math.min(Math.round(cropHeight), source.getHeight() - Math.round(view.cropY)));
		return ImageUtil.resizeImage(crop, view.width, view.height);
	}

	private BufferedImage loadImage(String file)
	{
		return this.loadedImages.computeIfAbsent(file, f -> ImageUtil.loadImageResource(TeleportMapsPlugin.class, f));
	}

	private JewelleryBoxCategoryDefinition findSkillsCategory()
	{
		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
		{
			if (category.usesSkillsNecklaceMap())
				return category;
		}
		throw new IllegalStateException("No Skills necklace category");
	}

	private int indexOf(JewelleryBoxCategoryDefinition category)
	{
		for (int i = 0; i < this.categoryDefinitions.length; i++)
		{
			if (this.categoryDefinitions[i] == category)
				return i;
		}
		throw new IllegalArgumentException(category.getName());
	}

	/**
	 * Creates the map background, generating its sprite. A sprite is only
	 * regenerated when what it shows (its key) or its size changes.
	 */
	private void createMapWidget(Widget container, MapView view, int spriteId, String key, Supplier<BufferedImage> image)
	{
		String fullKey = key + " " + view.width + "x" + view.height;
		if (!fullKey.equals(this.mapSpriteKeys.get(spriteId)))
		{
			this.mapSpriteKeys.put(spriteId, fullKey);
			SpriteVariants.registerImage(this.client, image.get(), spriteId, null, null, null);
		}

		Widget map = this.createSpriteWidget(container, view.width, view.height, view.x, view.y, spriteId);
		// Set hasListener / noClickThrough to disallow click-through
		map.setHasListener(true);
		map.setNoClickThrough(true);
	}

	/**
	 * @param labelled whether each destination is labelled on the map, rather
	 *                 than named in a tooltip on hover
	 */
	private void createWorldMapMarkers(Widget container, MapView view, JewelleryBoxCategoryDefinition category, boolean labelled)
	{
		int spriteBase = this.getSpriteBase(category);
		int width = JewelleryBoxDefinition.getWidth();
		int height = JewelleryBoxDefinition.getHeight();

		for (JewelleryBoxDefinition definition : category.getDestinations())
		{
			// Markers keep their size when the map is scaled, so place them by their centres
			this.createMarker(container, view, category, definition.getName(),
				view.toBoxX(definition.getX()) - width / 2, view.toBoxY(definition.getY()) - height / 2,
				width, height,
				spriteBase - MARKER_SPRITE, spriteBase - MARKER_HOVER_SPRITE, spriteBase - MARKER_DISABLED_SPRITE,
				!labelled ? null : definition.getLabel() == null ? LABEL_BELOW : definition.getLabel());
		}
	}

	/**
	 * @param labelPlacement where to label the destination on the map, or
	 *                       null to name it in a tooltip on hover instead
	 */
	private void createMarker(Widget container, MapView view, JewelleryBoxCategoryDefinition category, String name,
		int x, int y, int width, int height, int sprite, int hoverSprite, int disabledSprite, String labelPlacement)
	{
		UITeleport teleport = new UITeleport(container.createChild(-1, WidgetType.GRAPHIC), container.createChild(-1, WidgetType.GRAPHIC));
		teleport.setTeleportSprites(sprite, hoverSprite, disabledSprite);
		teleport.setPosition(x, y);
		teleport.setSize(width, height);
		teleport.setName(name);

		BoxEntry entry = this.availableEntries.get(name);
		boolean usable = entry != null && !entry.locked;

		if (entry == null)
		{
			// The box's tier can't hold this jewellery type
			teleport.setLocked(true);
			teleport.addAction(EXAMINE_ACTION, () -> this.triggerMessage(String.format("Your jewellery box needs upgrading to hold a %s.", category.getName())));
		}
		else if (entry.locked)
		{
			teleport.setLocked(true);
			teleport.addAction(EXAMINE_ACTION, () -> this.triggerMessage(String.format("You are unable to travel to %s.", name)));
		}
		else
		{
			teleport.addAction(TRAVEL_ACTION, () -> this.triggerTeleport(entry));
		}

		if (labelPlacement != null)
		{
			// Labelled like the Xeric's talisman map: "1. Castle Wars"
			String text = usable && config.displayHotkeys() ? entry.keyShortcut + ". " + name : name;
			this.createLabel(container, view, text, usable, labelPlacement, x, y, width, height);
		}
		else
		{
			String text = usable && config.displayHotkeys() ? entry.keyShortcut + ": " + name : name;
			teleport.addOnHoverListener((src) -> this.showTooltip(text, view, x, y, width));
			teleport.addOnLeaveListener((src) -> this.hideTooltip());
		}

		this.addTeleport(teleport);
	}

	/**
	 * Labels a destination beside its marker, kept within the map
	 */
	private void createLabel(Widget container, MapView view, String text, boolean usable, String placement,
		int markerX, int markerY, int markerWidth, int markerHeight)
	{
		int width = text.length() * LABEL_CHAR_WIDTH + LABEL_PADDING;
		int x;
		int y;
		int alignment = WidgetTextAlignment.CENTER;

		switch (placement)
		{
			case LABEL_ABOVE:
				x = markerX + markerWidth / 2 - width / 2;
				y = markerY - LABEL_HEIGHT;
				break;
			case LABEL_LEFT:
				x = markerX - width - LABEL_GAP;
				y = markerY + markerHeight / 2 - LABEL_HEIGHT / 2;
				alignment = WidgetTextAlignment.RIGHT;
				break;
			case LABEL_RIGHT:
				x = markerX + markerWidth + LABEL_GAP;
				y = markerY + markerHeight / 2 - LABEL_HEIGHT / 2;
				alignment = WidgetTextAlignment.LEFT;
				break;
			default:
				x = markerX + markerWidth / 2 - width / 2;
				y = markerY + markerHeight;
				break;
		}

		Rectangle area = view.labelArea;
		x = Math.max(area.x, Math.min(area.x + area.width - width, x));
		y = Math.max(area.y, Math.min(area.y + area.height - LABEL_HEIGHT, y));

		Widget label = container.createChild(-1, WidgetType.TEXT);
		label.setText(text);
		label.setFontId(FontID.PLAIN_11);
		label.setTextColor(usable ? Color.white.getRGB() : LABEL_LOCKED_COLOUR);
		label.setTextShadowed(true);
		label.setXTextAlignment(alignment);
		label.setYTextAlignment(WidgetTextAlignment.CENTER);
		setBounds(label, x, y, width, LABEL_HEIGHT);
	}

	private void createTabStrip(Widget container, Rectangle strip)
	{
		int tabWidth = (strip.width - TAB_GAP * (this.tabs.size() - 1)) / this.tabs.size();

		for (int i = 0; i < this.tabs.size(); i++)
		{
			Tab tab = this.tabs.get(i);
			final int tabIndex = i;
			int x = strip.x + i * (tabWidth + TAB_GAP);
			int y = strip.y;
			int spriteBase = this.getSpriteBase(tab.category);

			tab.background = container.createChild(-1, WidgetType.RECTANGLE);
			tab.background.setFilled(true);
			tab.background.setTextColor(TAB_COLOUR);
			setBounds(tab.background, x, y, tabWidth, TAB_HEIGHT);

			tab.icon = container.createChild(-1, WidgetType.GRAPHIC);
			tab.icon.setSpriteId(spriteBase - (tab.available ? TAB_ICON_SPRITE : TAB_ICON_DISABLED_SPRITE));
			setBounds(tab.icon, x + TAB_ICON_PADDING, y + (TAB_HEIGHT - TAB_ICON_HEIGHT) / 2, TAB_ICON_WIDTH, TAB_ICON_HEIGHT);

			int labelX = x + TAB_ICON_PADDING + TAB_ICON_WIDTH;
			tab.label = container.createChild(-1, WidgetType.TEXT);
			tab.label.setText(tab.category.getShortName());
			tab.label.setFontId(FontID.PLAIN_11);
			tab.label.setTextShadowed(true);
			tab.label.setXTextAlignment(WidgetTextAlignment.CENTER);
			tab.label.setYTextAlignment(WidgetTextAlignment.CENTER);
			setBounds(tab.label, labelX, y, x + tabWidth - labelX, TAB_HEIGHT);

			// A transparent button over the whole tab takes the clicks
			Widget clickArea = container.createChild(-1, WidgetType.RECTANGLE);
			clickArea.setOpacity(255);
			setBounds(clickArea, x, y, tabWidth, TAB_HEIGHT);
			UIButton button = new UIButton(clickArea);
			button.setName(tab.category.getName());

			if (tab.available)
			{
				button.addAction(VIEW_ACTION, () -> this.selectTab(tabIndex));
				button.addOnHoverListener((src) -> this.styleTab(tabIndex, true));
				button.addOnLeaveListener((src) -> this.styleTab(tabIndex, false));
			}
			else
			{
				button.addAction(EXAMINE_ACTION, () -> this.triggerMessage(String.format("Your jewellery box needs upgrading to hold a %s.", tab.category.getName())));
			}

			this.styleTab(i, false);
		}
	}

	private static void setBounds(Widget widget, int x, int y, int width, int height)
	{
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.revalidate();
	}

	private void styleTab(int tabIndex, boolean hovered)
	{
		Tab tab = this.tabs.get(tabIndex);

		if (tabIndex == this.selectedTab)
		{
			tab.background.setOpacity(TAB_SELECTED_OPACITY);
			tab.label.setTextColor(TAB_SELECTED_TEXT_COLOUR);
		}
		else
		{
			tab.background.setOpacity(hovered ? TAB_HOVER_OPACITY : TAB_OPACITY);
			tab.label.setTextColor(tab.available ? TAB_TEXT_COLOUR : TAB_DISABLED_TEXT_COLOUR);
		}
	}

	private void selectTab(int tabIndex)
	{
		if (tabIndex == this.selectedTab)
			return;

		int previous = this.selectedTab;
		this.selectedTab = tabIndex;
		this.hideTooltip();

		this.setTabContentVisible(this.tabs.get(previous), false);
		this.setTabContentVisible(this.tabs.get(tabIndex), true);
		this.styleTab(previous, false);
		this.styleTab(tabIndex, false);
	}

	private void setTabContentVisible(Tab tab, boolean visible)
	{
		for (Widget widget : tab.content)
			widget.setHidden(!visible);
	}

	private void createTooltip(Widget container)
	{
		this.tooltipBackground = container.createChild(-1, WidgetType.RECTANGLE);
		this.tooltipBackground.setFilled(true);
		this.tooltipBackground.setTextColor(TOOLTIP_COLOUR);
		this.tooltipBackground.setOpacity(TOOLTIP_OPACITY);
		this.tooltipBackground.setHidden(true);

		this.tooltipText = container.createChild(-1, WidgetType.TEXT);
		this.tooltipText.setFontId(FontID.PLAIN_11);
		this.tooltipText.setTextColor(Color.white.getRGB());
		this.tooltipText.setTextShadowed(true);
		this.tooltipText.setXTextAlignment(WidgetTextAlignment.CENTER);
		this.tooltipText.setYTextAlignment(WidgetTextAlignment.CENTER);
		this.tooltipText.setHidden(true);
	}

	/**
	 * Shows the destination name above its marker, kept within the map
	 */
	private void showTooltip(String text, MapView view, int markerX, int markerY, int markerWidth)
	{
		if (this.tooltipText == null)
			return;

		int width = text.length() * TOOLTIP_CHAR_WIDTH + TOOLTIP_PADDING;
		int x = markerX + markerWidth / 2 - width / 2;
		x = Math.max(view.x, Math.min(view.x + view.width - width, x));
		int y = Math.max(view.y, markerY - TOOLTIP_HEIGHT - TOOLTIP_GAP);

		for (Widget widget : new Widget[] {this.tooltipBackground, this.tooltipText})
		{
			setBounds(widget, x, y, width, TOOLTIP_HEIGHT);
			widget.setHidden(false);
		}
		this.tooltipText.setText(text);
	}

	private void hideTooltip()
	{
		if (this.tooltipText == null)
			return;

		this.tooltipBackground.setHidden(true);
		this.tooltipText.setHidden(true);
	}

	private int getSpriteBase(JewelleryBoxCategoryDefinition category)
	{
		return ITEM_SPRITE_BASE_ID - this.indexOf(category) * ITEM_SPRITE_BLOCK;
	}

	/**
	 * Builds the marker and tab icon sprites from each jewellery type's item
	 * icon, so the map needs no art of its own
	 */
	private void requestItemSprites()
	{
		if (this.itemSpritesRequested)
			return;

		this.itemSpritesRequested = true;

		for (JewelleryBoxCategoryDefinition category : this.categoryDefinitions)
		{
			int spriteBase = this.getSpriteBase(category);
			AsyncBufferedImage icon = this.itemManager.getImage(category.getItemId());
			icon.onLoaded(() -> this.registerItemSprites(icon, spriteBase));
		}
	}

	private void registerItemSprites(BufferedImage icon, int spriteBase)
	{
		BufferedImage marker = ImageUtil.resizeImage(icon, JewelleryBoxDefinition.getWidth(), JewelleryBoxDefinition.getHeight());
		SpriteVariants.registerImage(this.client, marker,
			spriteBase - MARKER_SPRITE,
			spriteBase - MARKER_HOVER_SPRITE,
			null,
			spriteBase - MARKER_DISABLED_SPRITE);

		BufferedImage tabIcon = ImageUtil.resizeImage(icon, TAB_ICON_WIDTH, TAB_ICON_HEIGHT);
		SpriteVariants.registerImage(this.client, tabIcon,
			spriteBase - TAB_ICON_SPRITE,
			null,
			null,
			spriteBase - TAB_ICON_DISABLED_SPRITE);
	}

	/**
	 * Presses the jewellery box button by dispatching the op listener the
	 * game attached to it, as if its first option had been clicked
	 */
	private void triggerTeleport(BoxEntry entry)
	{
		this.clientThread.invokeLater(() ->
		{
			Object[] template = entry.widget.getOnOpListener();
			if (template == null)
			{
				log.debug("No op listener on jewellery box button '{}'", entry.widget.getText());
				return;
			}

			Object[] listener = new Object[template.length];
			for (int i = 0; i < template.length; i++)
			{
				Object arg = template[i];
				if (arg instanceof Integer)
				{
					switch ((Integer) arg)
					{
						case ScriptEvent.MENU_OP:
							arg = 1;
							break;
						case ScriptEvent.WIDGET_ID:
							arg = entry.widget.getId();
							break;
						case ScriptEvent.WIDGET_INDEX:
							arg = entry.widget.getIndex();
							break;
						default:
							break;
					}
				}
				listener[i] = arg;
			}

			this.client.runScript(listener);
		});
	}

	private void triggerMessage(String message)
	{
		this.clientThread.invokeLater(() -> this.client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null));
	}
}
