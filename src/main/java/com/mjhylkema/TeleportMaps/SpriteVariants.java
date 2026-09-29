package com.mjhylkema.TeleportMaps;

import com.mjhylkema.TeleportMaps.definition.SpriteDefinition;
import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.util.ImageUtil;

/**
 * Generates the hover, selected and disabled sprite variants each map uses
 * from its base image, so only the base art needs to be shipped.
 */
public class SpriteVariants
{
	private static final Color HOVER_STROKE_INNER = new Color(188, 144, 0);
	private static final Color HOVER_STROKE_OUTER = new Color(255, 191, 0);
	private static final Color SELECTED_STROKE_INNER = new Color(86, 150, 44);
	private static final Color SELECTED_STROKE_OUTER = new Color(106, 238, 18);
	private static final float DISABLED_BRIGHTNESS = 0.70f;

	private SpriteVariants()
	{
	}

	static void register(Client client, SpriteDefinition[] definitions)
	{
		for (SpriteDefinition definition : definitions)
		{
			if (definition.getHoverSpriteId() == null
				&& definition.getSelectedSpriteId() == null
				&& definition.getDisabledSpriteId() == null)
				continue;

			BufferedImage base = ImageUtil.loadImageResource(TeleportMapsPlugin.class, definition.getFileName());
			registerVariants(client, base, definition.getHoverSpriteId(), definition.getSelectedSpriteId(), definition.getDisabledSpriteId());
		}
	}

	/**
	 * Registers an image built at runtime (e.g. an item icon) as a sprite,
	 * along with any of its requested variants
	 */
	public static void registerImage(Client client, BufferedImage base, int spriteId, Integer hoverSpriteId, Integer selectedSpriteId, Integer disabledSpriteId)
	{
		registerSprite(client, spriteId, base);
		registerVariants(client, base, hoverSpriteId, selectedSpriteId, disabledSpriteId);
	}

	private static void registerVariants(Client client, BufferedImage base, Integer hoverSpriteId, Integer selectedSpriteId, Integer disabledSpriteId)
	{
		if (hoverSpriteId != null)
			registerSprite(client, hoverSpriteId, outline(vibrant(base), HOVER_STROKE_INNER, HOVER_STROKE_OUTER));

		if (selectedSpriteId != null)
			registerSprite(client, selectedSpriteId, outline(base, SELECTED_STROKE_INNER, SELECTED_STROKE_OUTER));

		if (disabledSpriteId != null)
			registerSprite(client, disabledSpriteId, grayscale(base));
	}

	private static void registerSprite(Client client, int spriteId, BufferedImage image)
	{
		client.getSpriteOverrides().put(spriteId, ImageUtil.getImageSpritePixels(image, client));
	}

	/**
	 * Adds a two pixel stroke around the image's shape: a bright inner
	 * ring with a darker outer ring
	 */
	private static BufferedImage outline(BufferedImage image, Color inner, Color outer)
	{
		return ImageUtil.outlineImage(ImageUtil.outlineImage(image, inner, true), outer, true);
	}

	private static BufferedImage grayscale(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int a = (argb >>> 24);
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;
				int grey = (int) ((0.299 * r + 0.587 * g + 0.114 * b) * DISABLED_BRIGHTNESS);
				out.setRGB(x, y, (a << 24) | (grey << 16) | (grey << 8) | grey);
			}
		}
		return out;
	}

	private static BufferedImage vibrant(BufferedImage image)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int argb = image.getRGB(x, y);
				int a = (argb >>> 24);
				int r = (argb >> 16) & 0xFF;
				int g = (argb >> 8) & 0xFF;
				int b = argb & 0xFF;

				float[] hsb = Color.RGBtoHSB(r, g, b, null);
				hsb[1] = Math.min(1.0f, hsb[1] * 1.5f);
				hsb[2] = Math.min(1.0f, hsb[2] * 1.1f);

				int rgb = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
				out.setRGB(x, y, (a << 24) | (rgb & 0x00FFFFFF));
			}
		}
		return out;
	}
}
