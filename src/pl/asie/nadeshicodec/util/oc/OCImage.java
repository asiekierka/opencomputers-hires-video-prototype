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

import java.awt.image.BufferedImage;

public class OCImage {
	private final int widthChars;
	private final int heightChars;
	private final int[] palette;

	private final byte[] quadrants;
	private final byte[] bgColors;
	private final byte[] fgColors;

	public OCImage(int widthChars, int heightChars, int[] palette) {
		this.widthChars = widthChars;
		this.heightChars = heightChars;
		this.palette = palette;

		this.quadrants = new byte[widthChars * heightChars];
		this.bgColors = new byte[widthChars * heightChars];
		this.fgColors = new byte[widthChars * heightChars];
	}

	public OCImage copy() {
		OCImage newImage = new OCImage(widthChars, heightChars, palette);
		System.arraycopy(quadrants, 0, newImage.quadrants, 0, widthChars * heightChars);
		System.arraycopy(bgColors, 0, newImage.bgColors, 0, widthChars * heightChars);
		System.arraycopy(fgColors, 0, newImage.fgColors, 0, widthChars * heightChars);
		return newImage;
	}

	public int getWidthChars() {
		return widthChars;
	}

	public int getHeightChars() {
		return heightChars;
	}

	public int getWidthPixels() {
		return widthChars * 2;
	}

	public int getHeightPixels() {
		return heightChars * 4;
	}

	public int getQuadrant(int x, int y) {
		int p = y * widthChars + x;
		if (p < 0 || p >= bgColors.length) return 0;
		return ((int) quadrants[p]) & 0xFF;
	}

	public int getBG(int x, int y) {
		int p = y * widthChars + x;
		if (p < 0 || p >= bgColors.length) return 0;
		return ((int) bgColors[p]) & 0xFF;
	}

	public int getFG(int x, int y) {
		int p = y * widthChars + x;
		if (p < 0 || p >= bgColors.length) return 0;
		return ((int) fgColors[p]) & 0xFF;
	}

	public void set(int x, int y, int bg, int fg, int q) {
		int p = y * widthChars + x;
		if (p < 0 || p >= bgColors.length) return;

		quadrants[p] = (byte) q;
		bgColors[p] = (byte) bg;
		fgColors[p] = (byte) fg;
	}

	public BufferedImage getPreview() {
		BufferedImage image = new BufferedImage(widthChars * 2, heightChars * 4, BufferedImage.TYPE_INT_RGB);

		for (int y = 0; y < heightChars; y++) {
			for (int x = 0; x < widthChars; x++) {
				int qp = y * widthChars + x;
				for (int p = 0; p < 8; p++) {
					boolean v = ((((int) quadrants[qp]) >> (7 - p)) & 1) != 0;
					image.setRGB(
						x*2 + (p & 1), y*4 + (p >> 1),
						v ? palette[((int) fgColors[qp]) & 0xFF] : palette[((int) bgColors[qp]) & 0xFF]
					);
				}
			}
		}

		return image;
	}

	public int[] getPalette() {
		return palette;
	}
}
