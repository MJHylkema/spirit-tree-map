package com.mjhylkema.TeleportMaps;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Draws a region of the Spirit Tree world map in the style of the hand drawn
 * scroll maps (Xeric's talisman, Wilderness obelisks): a blank parchment
 * scroll with the region's land outlined on it.
 */
public final class ScrollMapRenderer
{
	/* The scroll template: the Xeric's talisman map, with its land painted
	   over. Between the rolled sides every column is repainted with the
	   parchment colour sampled from a row clear of any land. */
	public static final String TEMPLATE_FILE = "/XericsMap/XericsMap.png";
	private static final int TEMPLATE_WIDTH = 509;
	private static final int TEMPLATE_HEIGHT = 317;
	private static final int TEMPLATE_ROLL_LEFT = 20;
	private static final int TEMPLATE_ROLL_RIGHT = 488;
	private static final int TEMPLATE_TORN_TOP = 20;
	private static final int TEMPLATE_TORN_BOTTOM = 298;
	private static final int TEMPLATE_PARCHMENT_ROW = 30;
	private static final int PARCHMENT_SAMPLE_RADIUS = 3;
	private static final int PARCHMENT_SMOOTHING = 4;
	private static final int DEFAULT_PARCHMENT = 0xb29e79;

	/* The world map's land is darker than its sea */
	private static final int LAND_MAX_RED = 162;

	/* Land colours, relative to the parchment beneath, matching the scroll maps */
	private static final float[] LAND_FILL = {0.87f, 0.82f, 0.69f};
	private static final float[] LAND_OUTLINE = {0.47f, 0.46f, 0.31f};
	private static final int OUTLINE_WIDTH = 3;

	private ScrollMapRenderer()
	{
	}

	/**
	 * The area of a scroll of the given size that's clear parchment: between
	 * the rolled sides and the torn top and bottom edges
	 */
	public static Rectangle innerBounds(int width, int height)
	{
		int left = Math.round(TEMPLATE_ROLL_LEFT * (float) width / TEMPLATE_WIDTH) + OUTLINE_WIDTH;
		int right = Math.round(TEMPLATE_ROLL_RIGHT * (float) width / TEMPLATE_WIDTH) - OUTLINE_WIDTH;
		int top = Math.round(TEMPLATE_TORN_TOP * (float) height / TEMPLATE_HEIGHT);
		int bottom = Math.round(TEMPLATE_TORN_BOTTOM * (float) height / TEMPLATE_HEIGHT);
		return new Rectangle(left, top, right - left, bottom - top);
	}

	/**
	 * Renders a region of the world map onto a scroll of the given size, into
	 * the given area of the scroll. The region's aspect ratio should match
	 * the area's. Land reaching the torn edges is clipped to them.
	 */
	public static BufferedImage render(BufferedImage template, BufferedImage world, float cropX, float cropY, float cropWidth, float cropHeight,
		int width, int height, Rectangle area)
	{
		BufferedImage scroll = resize(template, width, height);
		int[] parchment = sampleParchment(scroll, Math.round(TEMPLATE_PARCHMENT_ROW * (float) height / TEMPLATE_HEIGHT));
		int rollLeft = Math.round(TEMPLATE_ROLL_LEFT * (float) width / TEMPLATE_WIDTH);
		int rollRight = Math.round(TEMPLATE_ROLL_RIGHT * (float) width / TEMPLATE_WIDTH);

		// Which pixels of the area are land
		boolean[][] land = new boolean[width][height];
		for (int x = Math.max(0, area.x); x < Math.min(width, area.x + area.width); x++)
		{
			for (int y = Math.max(0, area.y); y < Math.min(height, area.y + area.height); y++)
			{
				int worldX = (int) (cropX + (x - area.x + 0.5f) * cropWidth / area.width);
				int worldY = (int) (cropY + (y - area.y + 0.5f) * cropHeight / area.height);
				land[x][y] = isLand(world, worldX, worldY);
			}
		}

		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < width; x++)
		{
			for (int y = 0; y < height; y++)
			{
				int argb = scroll.getRGB(x, y);
				int alpha = argb >>> 24;

				// The rolled sides and the transparent torn edges are kept as is
				if (alpha == 0 || x < rollLeft || x >= rollRight)
				{
					out.setRGB(x, y, argb);
					continue;
				}

				int colour = parchment[x];
				if (land[x][y])
					colour = tint(colour, isCoast(land, area, x, y) ? LAND_OUTLINE : LAND_FILL);

				out.setRGB(x, y, (alpha << 24) | colour);
			}
		}

		return out;
	}

	/**
	 * Chooses the region of the world map to show: the given points, padded,
	 * widened to the given aspect ratio and to at least the minimum width,
	 * and kept within the bounds.
	 * @return the region as {x, y, width, height}
	 */
	public static float[] cropAround(int[] xs, int[] ys, float aspect, float padding, float minWidth, Rectangle bounds)
	{
		float left = Float.MAX_VALUE, top = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
		for (int i = 0; i < xs.length; i++)
		{
			left = Math.min(left, xs[i] - padding);
			right = Math.max(right, xs[i] + padding);
			top = Math.min(top, ys[i] - padding);
			bottom = Math.max(bottom, ys[i] + padding);
		}

		float width = Math.max(Math.max(right - left, (bottom - top) * aspect), minWidth);
		width = Math.min(width, Math.min(bounds.width, bounds.height * aspect));
		float height = width / aspect;
		float x = clamp((left + right) / 2 - width / 2, bounds.x, bounds.x + bounds.width - width);
		float y = clamp((top + bottom) / 2 - height / 2, bounds.y, bounds.y + bounds.height - height);
		return new float[] {x, y, width, height};
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static BufferedImage resize(BufferedImage image, int width, int height)
	{
		BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = out.createGraphics();
		graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		graphics.drawImage(image, 0, 0, width, height, null);
		graphics.dispose();
		return out;
	}

	/**
	 * The parchment colour of each column, from rows clear of land. Each
	 * column takes the brightest opaque pixel around the row, skipping the
	 * dark specks and torn gaps, then the columns are smoothed so the
	 * parchment's shading stays but no single column stands out.
	 */
	private static int[] sampleParchment(BufferedImage scroll, int row)
	{
		int width = scroll.getWidth();
		int[] sampled = new int[width];
		int last = DEFAULT_PARCHMENT;
		for (int x = 0; x < width; x++)
		{
			int brightest = -1;
			for (int y = Math.max(0, row - PARCHMENT_SAMPLE_RADIUS); y <= Math.min(scroll.getHeight() - 1, row + PARCHMENT_SAMPLE_RADIUS); y++)
			{
				int argb = scroll.getRGB(x, y);
				if ((argb >>> 24) == 255 && (brightest < 0 || luminance(argb) > luminance(brightest)))
					brightest = argb & 0xffffff;
			}
			if (brightest >= 0)
				last = brightest;
			sampled[x] = last;
		}

		int[] parchment = new int[width];
		for (int x = 0; x < width; x++)
		{
			int r = 0, g = 0, b = 0, count = 0;
			for (int nx = Math.max(0, x - PARCHMENT_SMOOTHING); nx <= Math.min(width - 1, x + PARCHMENT_SMOOTHING); nx++)
			{
				r += (sampled[nx] >> 16) & 0xff;
				g += (sampled[nx] >> 8) & 0xff;
				b += sampled[nx] & 0xff;
				count++;
			}
			parchment[x] = ((r / count) << 16) | ((g / count) << 8) | (b / count);
		}
		return parchment;
	}

	private static int luminance(int rgb)
	{
		return ((rgb >> 16) & 0xff) * 299 + ((rgb >> 8) & 0xff) * 587 + (rgb & 0xff) * 114;
	}

	private static boolean isLand(BufferedImage world, int x, int y)
	{
		if (x < 0 || y < 0 || x >= world.getWidth() || y >= world.getHeight())
			return false;

		int argb = world.getRGB(x, y);
		return (argb >>> 24) > 0 && ((argb >> 16) & 0xff) <= LAND_MAX_RED;
	}

	/**
	 * Whether a land pixel lies within the outline width of the sea
	 */
	private static boolean isCoast(boolean[][] land, Rectangle content, int x, int y)
	{
		for (int dx = -OUTLINE_WIDTH; dx <= OUTLINE_WIDTH; dx++)
		{
			for (int dy = -OUTLINE_WIDTH; dy <= OUTLINE_WIDTH; dy++)
			{
				if (dx * dx + dy * dy > OUTLINE_WIDTH * OUTLINE_WIDTH)
					continue;

				// Land running off the content area isn't coast
				int nx = x + dx;
				int ny = y + dy;
				if (content.contains(nx, ny) && !land[nx][ny])
					return true;
			}
		}
		return false;
	}

	private static int tint(int rgb, float[] factor)
	{
		int r = Math.round(((rgb >> 16) & 0xff) * factor[0]);
		int g = Math.round(((rgb >> 8) & 0xff) * factor[1]);
		int b = Math.round((rgb & 0xff) * factor[2]);
		return (r << 16) | (g << 8) | b;
	}
}
