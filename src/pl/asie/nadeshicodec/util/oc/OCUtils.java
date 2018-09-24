/*
 * Copyright (c) 2018 Adrian Siekierka
 *
 * This file is part of Nadeshicodec.
 *
 * Nadeshicodec is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nadeshicodec is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Nadeshicodec.  If not, see <http://www.gnu.org/licenses/>.
 */

package pl.asie.nadeshicodec.util.oc;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import pl.asie.nadeshicodec.util.DitherMatrix;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.colorspace.Colorspace;
import pl.asie.nadeshicodec.util.colorspace.Colorspaces;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

public final class OCUtils {
	private static final int[] PALETTE_TIER_3;
	private static final int[][] closestColorTableTier3;

	static {
		PALETTE_TIER_3 = new int[256];
		for (int i = 0; i < 16; i++) {
			PALETTE_TIER_3[i] = ((i + 1) * 255 / 17) * 0x10101;
		}
		for (int i = 0; i < 240; i++) {
			int b = (i % 5) * 255 / 4;
			int g = ((i / 5) % 8) * 255 / 7;
			int r = ((i / 40) % 6) * 255 / 5;
			PALETTE_TIER_3[i + 16] = (r << 16) | (g << 8) | b;
		}
		closestColorTableTier3 = new int[32768][];
		for (int i = 0; i < 32768; i++) {
			int rgb888 = 0;
			rgb888 |= (i & 31) * 255 / 31;
			rgb888 |= (((i >> 5) & 31) * 255 / 31) << 8;
			rgb888 |= (((i >> 10) & 31) * 255 / 31) << 16;
			closestColorTableTier3[i] = getClosestSlow(rgb888, PALETTE_TIER_3);
		}
	}

	private OCUtils() {}

	public static int[] getPaletteTier3() {
		return PALETTE_TIER_3;
	}

	public static int[] getClosestSlow(int color, int[] palette) {
		List<int[]> distances = new ArrayList<>(256);

		for (int i = 0; i < 256; i++) {
			if (i <= 12 && (i & 1) == 0) continue;

			int dist = ImageUtils.cheapColorDistance(palette[i], color);
			if (i < 16) dist *= 20;
			distances.add(new int[] { i, dist });
		}

		distances.sort(Comparator.comparingInt(a -> a[1]));

		int[] result = new int[4];
		for (int i = 0; i < result.length; i++) {
			result[i] = distances.get(i)[0];
		}

		return result;
	}

	public static int[] getClosest(int color, int[] palette) {
		if (palette == PALETTE_TIER_3) {
			int r = Math.round(((color >> 16) & 0xFF) * 31.0f / 255);
			int g = Math.round(((color >> 8) & 0xFF) * 31.0f / 255);
			int b = Math.round((color & 0xFF) * 31.0f / 255);
			return closestColorTableTier3[(r << 10) | (g << 5) | b];

			/* int r = Math.round(((color >> 16) & 0xFF) * 5.0f / 255);
			int g = Math.round(((color >> 8) & 0xFF) * 7.0f / 255);
			int b = Math.round((color & 0xFF) * 4.0f / 255);
			return 16 + r * 40 + g * 5 + b; */
		} else {
			throw new RuntimeException("TODO: support custom palettes");
		}
	}

	private static double ditherDistance(int rgb, int rgb1, int rgb2) {
		// inspired by the teachings of Bisqwit
		// http://bisqwit.iki.fi/story/howto/dither/jy/
		if (rgb1 == rgb2) {
			return 0.5;
		}

		int r = (rgb >> 16) & 0xFF;
		int g = (rgb >> 8) & 0xFF;
		int b = (rgb) & 0xFF;
		int r1 = (rgb1 >> 16) & 0xFF;
		int g1 = (rgb1 >> 8) & 0xFF;
		int b1 = (rgb1) & 0xFF;
		int r2 = (rgb2 >> 16) & 0xFF;
		int g2 = (rgb2 >> 8) & 0xFF;
		int b2 = (rgb2) & 0xFF;
		/* float[] f = Colorspaces.convertFromRGB(rgb, Colorspace.YUV);
		float[] f1 = Colorspaces.convertFromRGB(rgb1, Colorspace.YUV);
		float[] f2 = Colorspaces.convertFromRGB(rgb2, Colorspace.YUV);
		float r = f[0];
		float g = f[1];
		float b = f[2];
		float r1 = f1[0];
		float g1 = f1[1];
		float b1 = f1[2];
		float r2 = f2[0];
		float g2 = f2[1];
		float b2 = f2[2]; */

		return (r * r1 - r * r2 - r1 * r2 + r2 * r2 +
				g * g1 - g * g2 - g1 * g2 + g2 * g2 +
				b * b1 - b * b2 - b1 * b2 + b2 * b2) / (double) (
				((r1 - r2) * (r1 - r2) +
						(g1 - g2) * (g1 - g2) +
						(b1 - b2) * (b1 - b2)) );
	}

	// based on the fast pngview algorithm
	public static OCImage from(BufferedImage image, int[] palette, DitherMatrix dither) {
		OCImage img = new OCImage((image.getWidth() + 1) / 2, (image.getHeight() + 3) / 4, palette);

		IntStream.rangeClosed(0, img.getHeightChars() - 1).forEach((y) -> {
			int[] pixelsRGB = new int[8];
			int[] irDiff = new int[8];

			for (int x = 0; x < img.getWidthChars(); x++) {
				IntSet colors = new IntOpenHashSet();
				for (int p = 0; p < 8; p++) {
					pixelsRGB[p] = image.getRGB(x*2 + (p&1), y*4 + (p>>1));
					for (int v : getClosest(pixelsRGB[p], palette))
						colors.add(v);
				}

				int[] colorsA = colors.toIntArray();
				int bestI = 0;
				int bestJ = 0;
				int bestDistance = Integer.MAX_VALUE;

				for (int i = 0; i < colorsA.length - 1; i++) {
					int ir = palette[colorsA[i]];
					int selfDist = 0;
					for (int p = 0; p < 8; p++) {
						irDiff[p] = ImageUtils.cheapColorDistance(pixelsRGB[p], ir);
						selfDist += irDiff[p];
					}

					if (selfDist < bestDistance) {
						bestI = i;
						bestJ = i;
						bestDistance = selfDist;
					}

					for (int j = i + 1; j < colorsA.length; j++) {
						int jr = palette[colorsA[j]];
						int distance = 0;
						for (int p = 0; p < 8; p++) {
							distance += Math.min(irDiff[p], ImageUtils.cheapColorDistance(pixelsRGB[p], jr));
							if (distance >= bestDistance) {
								break;
							}
						}

						if (distance < bestDistance) {
							bestI = i;
							bestJ = j;
							bestDistance = distance;
						}
					}
				}

				int bgColor = colorsA[bestI];
				int fgColor = colorsA[bestJ];
				if (bgColor == fgColor) {
					img.set(x, y, bgColor, 0, 0);
				} else {
					int q = 0;

					int lastV = -1;
					int vChanges = 0;

					if (dither.isEnabled()) {
						int[] oDither = dither.getMatrix();
						int oDWidth = dither.getWidth();

						for (int p = 0; p < 8; p++) {
							int px = x * 2 + ((7 - p) & 1);
							int py = y * 4 + ((7 - p) >> 1);

							double dDist = ditherDistance(pixelsRGB[p], palette[bgColor], palette[fgColor]) - 0.5;
							dDist = (dDist * 2) + 0.5;

							int dDi = (int) Math.round(oDither.length - (dDist * oDither.length));
							if (dDi < 0) dDi = 0;
							else if (dDi >= oDWidth * oDWidth) dDi = oDWidth * oDWidth;

							int dThr = oDither[((py % oDWidth) * oDWidth) + (px % oDWidth)];

							if (lastV != dDi) {
								vChanges++;
								lastV = dDi;
							}

							//dDiSet.add(dDi);

							if (dThr < dDi) {
								q = (q << 1) | 1;
							} else {
								q = (q << 1);
							}
						}

						if (vChanges == 1) {
							if (lastV < (oDWidth * oDWidth / 2)) {
								q = 0;
							} else {
								q = 255;
							}
						}
					} else {
						for (int p = 0; p < 8; p++) {
							if (ImageUtils.cheapColorDistance(pixelsRGB[p], palette[fgColor]) < ImageUtils.cheapColorDistance(pixelsRGB[p], palette[bgColor]))
								q = (q << 1) | 1;
							else
								q = (q << 1);
						}
					}
					img.set(x, y, bgColor, fgColor, q);
				}

				//Int2IntOpenHashMap pixelCount = new Int2IntOpenHashMap();
				//pixelCount.defaultReturnValue(0);

				/* int maxContrast = 0;
				for (int p1 = 0; p1 < 8; p1++) {
					for (int p2 = p1 + 1; p2 < 8; p2++) {
						maxContrast = Math.max(maxContrast, ImageUtils.cheapColorDistance(pixelsRGB[p1], pixelsRGB[p2]));
					}
				}

				int SMALLEST_CONTRAST = ImageUtils.cheapColorDistance(0x303030, 0x3A3A3A);

				if (maxContrast < SMALLEST_CONTRAST) {
					int tp = Integer.MAX_VALUE;
					for (int p = 0; p < 8; p++) {
						pixels[p] = getClosest(pixelsRGB[p], palette)[0];
						if (pixels[p] < tp) {
							tp = pixels[p];
						}
					}
					pixelColors = 1;
					pixelCount.addTo(tp, 8);
				} else { */
					/* for (int p = 0; p < 8; p++) {
						pixels[p] = getClosest(pixelsRGB[p], palette)[0];
						if (pixelCount.addTo(pixels[p], 1) == 0) {
							pixelColors++;
						}
					} */
				//}

				/* pngview algorithm

				if (pixelColors > 2) {
					int bg = -1;
					int bgc = -1;
					int fg = -1;
					int fgc = -1;

					for (int k : pixelCount.keySet()) {
						int v = pixelCount.get(k);
						if (v > bgc) {
							bg = k;
							bgc = v;
						}
					}

					for (int k : pixelCount.keySet()) {
						if (k != bg) {
							int v = pixelCount.get(k);
							int contrast = ImageUtils.cheapColorDistance(palette[bg], palette[k]) * v;
							if (contrast > fgc) {
								fg = k;
								fgc = contrast;
							}
						}
					}

					pixelCount.clear();
					pixelCount.put(bg, 1);
					pixelCount.put(fg, 1);
					pixelColors = 2;
				}

				*/

				//if (pixelColors > 2) {

					//pixelCount.clear();
					//pixelCount.put(colorsA[bestI], 1);
					//pixelCount.put(colorsA[bestJ], 1);
					//pixelColors = 2;
				//}

				/* if (pixelColors == 1) {
					img.set(x, y, pixelCount.keySet().iterator().nextInt(), 0, 0);
				} else if (pixelColors == 2) { */
					/* IntIterator iterator = pixelCount.keySet().iterator();
					int bgColor = iterator.nextInt();
					int fgColor = iterator.nextInt(); */

				/* } else {
					throw new RuntimeException("Invalid pixelColors = " + pixelColors + "!");
				} */
			}
		});

		return img;
	}
}

