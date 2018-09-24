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

package pl.asie.nadeshicodec.util;

import pl.asie.nadeshicodec.util.colorspace.Colorspace;
import pl.asie.nadeshicodec.util.colorspace.Colorspaces;
import pl.asie.nadeshicodec.util.oc.OCImage;
import pl.asie.nadeshicodec.util.oc.OCUtils;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Collections;

public final class ImageUtils {
	private ImageUtils() {

	}

	public static double getDistance(BufferedImage first, BufferedImage second, int x, int y, int width, int height) {
		double v = 0;
		for (int iy = y; iy < y+height; iy++) {
			for (int ix = x; ix < x+width; ix++) {
				v += cheapColorDistance(first.getRGB(ix, iy), second.getRGB(ix, iy));
			}
		}

		return v;
	}

	public static int cheapColorDistance(int rgb1, int rgb2) {
		if (rgb1 == rgb2) {
			return 0;
		}

		int r1 = (rgb1 >> 16) & 0xFF;
		int g1 = (rgb1 >> 8) & 0xFF;
		int b1 = (rgb1) & 0xFF;
		int r2 = (rgb2 >> 16) & 0xFF;
		int g2 = (rgb2 >> 8) & 0xFF;
		int b2 = (rgb2) & 0xFF;
		int rs = (r1 - r2) * (r1 - r2);
		int gs = (g1 - g2) * (g1 - g2);
		int bs = (b1 - b2) * (b1 - b2);
		int rAvg = (r1 + r2) / 2;

		return (((512 + rAvg) * rs) >> 8) + (4 * gs) + (((767 - rAvg) * bs) >> 8);
	}

	// EXPERIMENTAL: 10% actual 1:1 matching, 90% "does the cluster of pixels roughly correspond to similar colors in the other cluster?"
	/* public static double getDistance(int bg1, int fg1, int q1, int bg2, int fg2, int q2, int[] palette) {
		double v = 0;
		if (bg1 != bg2 || fg1 != fg2 || q1 != q2) {
			boolean[] taken = new boolean[8];

			double distBgBg = cheapColorDistance(palette[bg1], palette[bg2]);
			double distBgFg = cheapColorDistance(palette[bg1], palette[fg2]);
			double distFgBg = cheapColorDistance(palette[fg1], palette[bg2]);
			double distFgFg = cheapColorDistance(palette[fg1], palette[fg2]);

			for (int i = 0; i < 8; i++) {
				boolean isFg1 = (((q1 >> i) & 1) != 0);
				boolean isFg2o = (((q2 >> i) & 1) != 0);
				int ju = i;
				double vv = isFg1 ? (isFg2o ? distFgFg : distFgBg) : (isFg2o ? distBgFg : distBgBg);
				v += 0.11 * vv;
				for (int j = 0; j < 8; j++) {
					if (taken[j] || i == j) continue;
					boolean isFg2 = (((q2 >> j) & 1) != 0);
					double d = isFg1 ? (isFg2 ? distFgFg : distFgBg) : (isFg2 ? distBgFg : distBgBg);
					if (d < vv) {
						ju = j;
						vv = d;
					}
				}
				taken[ju] = true;
				v += vv;
			}
		}
		return v;
	} */

	private static int[] distances;

	public static int cheapPaletteDistance(int a1, int a2, int[] palette) {
		if (distances == null) {
			distances = new int[65536];
			for (int f = 0; f < 256; f++) {
				for (int b = f + 1; b < 256; b++) {
					distances[(b << 8) | f] = cheapColorDistance(OCUtils.getPaletteTier3()[b], OCUtils.getPaletteTier3()[f]);
					distances[(f << 8) | b] = distances[(b << 8) | f];
				}
			}
		}

		if (palette == OCUtils.getPaletteTier3()) {
			return distances[(a1 << 8) | a2];
		} else {
			return cheapColorDistance(palette[a1], palette[a2]);
		}
	}

	private static int clamp(int x, int w) {
		if (x <= 0) return 0;
		else if (x >= w) return w-1;
		else return x;
	}

	public static BufferedImage medianFilter(BufferedImage image) {
		BufferedImage result = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

		float[] yComp = new float[25];
		float[] uComp = new float[25];
		float[] vComp = new float[25];

		for (int iy = 0; iy < result.getHeight(); iy++) {
			for (int ix = 0; ix < result.getWidth(); ix++) {
				int ip = 0;
				for (int iyo = -2; iyo <= 2; iyo++) {
					for (int ixo = -2; ixo <= 2; ixo++, ip++) {
						float[] yuv = Colorspaces.convertFromRGB(image.getRGB(
								clamp(ix+ixo, result.getWidth()), clamp(iy+iyo, result.getHeight())
						), Colorspace.YUV);
						yComp[ip] = yuv[0];
						uComp[ip] = yuv[1];
						vComp[ip] = yuv[2];
					}
				}

				Arrays.sort(yComp);
				Arrays.sort(uComp);
				Arrays.sort(vComp);

				if ((yComp[8] - yComp[0]) > 0.05f) {
					result.setRGB(ix, iy, image.getRGB(ix, iy));
				} else {
					float[] yuvOut = new float[]{yComp[4], uComp[4], vComp[4]};
					result.setRGB(ix, iy, Colorspaces.convertToRGB(yuvOut, Colorspace.YUV));
				}
			}
		}

		return result;
	}

	public static boolean hasDistance(int bg1, int fg1, int q1, int bg2, int fg2, int q2) {
		return  !((bg1 == fg2 && fg1 == bg2 && q1 == (q2 ^ 0xFF)) || (bg1 == bg2 && fg1 == fg2 && q1 == q2));
	}

	public static long getDistance(int bg1, int fg1, int q1, int bg2, int fg2, int q2, int[] palette) {
		if (distances == null) {
			distances = new int[65536];
			for (int f = 0; f < 256; f++) {
				for (int b = f + 1; b < 256; b++) {
					distances[(b << 8) | f] = cheapColorDistance(OCUtils.getPaletteTier3()[b], OCUtils.getPaletteTier3()[f]);
					distances[(f << 8) | b] = distances[(b << 8) | f];
				}
			}
		}

		if (bg1 == fg2 && fg1 == bg2) {
			int t = fg2;
			fg2 = bg2;
			bg2 = t;
			q2 ^= 0xFF;
		}

		long v = 0;
		if (q1 != q2 || bg1 != bg2 || fg1 != fg2) {
			if (palette == OCUtils.getPaletteTier3()) {
				int distBgBg = distances[(bg1 << 8) | bg2];
				int distFgFg = distances[(fg1 << 8) | fg2];

				if (q1 == q2) {
					int sb = MathUtils.setBits8(q1);
					v += (sb * distFgFg) + (8 - sb) * distBgBg;
				} else {
					int distBgFg = distances[(bg1 << 8) | fg2];
					int distFgBg = distances[(fg1 << 8) | bg2];

					v = v + (MathUtils.setBits8(q1 & q2)) * distFgFg
						  + (MathUtils.setBits8((q1^0xFF) & q2)) * distBgFg
					      + (MathUtils.setBits8(q1 & (q2^0xFF))) * distFgBg
					      + (MathUtils.setBits8((q1^0xFF) & (q2^0xFF))) * distBgBg;
				}
			} else {
				int distBgBg = cheapColorDistance(palette[bg1], palette[bg2]);
				int distBgFg = cheapColorDistance(palette[bg1], palette[fg2]);
				int distFgBg = cheapColorDistance(palette[fg1], palette[bg2]);
				int distFgFg = cheapColorDistance(palette[fg1], palette[fg2]);

				for (int i = 0; i < 8; i++) {
					boolean isFg1 = (((q1 >> i) & 1) != 0);
					boolean isFg2 = (((q2 >> i) & 1) != 0);
					v += isFg1 ? (isFg2 ? distFgFg : distFgBg) : (isFg2 ? distBgFg : distBgBg);
				}
			}
		}

		if (bg1 == bg2 && fg1 == fg2) {
			if ((q1 << 2) == (q2 & 0xFC)) {
				return v/3;
			}

			if ((q1 >> 2) == (q2 & 0x3F)) {
				return v/3;
			}
		}

		return v;
	}

	public static double getDistance(OCImage image, OCImage changedImage, int x, int y, int width, int height) {
		double v = 0;
		for (int iy = y; iy < y+height; iy++) {
			for (int ix = x; ix < x+width; ix++) {
				int bg1 = image.getBG(ix, iy);
				int fg1 = image.getFG(ix, iy);
				int q1 = image.getQuadrant(ix, iy);

				int bg2 = changedImage.getBG(ix, iy);
				int fg2 = changedImage.getFG(ix, iy);
				int q2 = image.getQuadrant(ix, iy);

				v += getDistance(bg1, fg1, q1, bg2, fg2, q2, image.getPalette());
			}
		}
		return v;
	}
}
