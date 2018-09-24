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

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.MathUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class OCCommandFillWithColor implements IOCCommand {
	private final int x, y;
	private final int width, height;
	private final int color;

	public OCCommandFillWithColor(int x, int y, int width, int height, int color) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.color = color;
	}

	public Optional<OCCommandFillWithColor> compress(IntSet itjs) {
		int x1 = this.getX();
		int y1 = this.getY();
		int x2 = x1 - 1 + this.getWidth();
		int y2 = y1 - 1 + this.getHeight();

		int ox1 = x1, ox2 = x2, oy1 = y1, oy2 = y2;

		// reduce size: top
		while (y1 < y2) {
			boolean canResize = true;
			for (int ix = x1; ix <= x2; ix++) {
				int ip = y1*160 + ix;
				if (!itjs.contains(ip)) {
					canResize = false;
					break;
				}
			}

			if (canResize) y1++; else break;
		}

		// reduce size: bottom
		while (y1 < y2) {
			boolean canResize = true;
			for (int ix = x1; ix <= x2; ix++) {
				int ip = y2*160 + ix;
				if (!itjs.contains(ip)) {
					canResize = false;
					break;
				}
			}

			if (canResize) y2--; else break;
		}

		// reduce size: left
		while (x1 < x2) {
			boolean canResize = true;
			for (int iy = y1; iy <= y2; iy++) {
				int ip = iy*160 + x1;
				if (!itjs.contains(ip)) {
					canResize = false;
					break;
				}
			}

			if (canResize) x1++; else break;
		}

		while (x1 < x2) {
			boolean canResize = true;
			for (int iy = y1; iy <= y2; iy++) {
				int ip = iy*160 + x2;
				if (!itjs.contains(ip)) {
					canResize = false;
					break;
				}
			}

			if (canResize) x2--; else break;
		}

		if (x1 != ox1 || x2 != ox2 || y1 != oy1 || y2 != oy2) {
			return Optional.of(new OCCommandFillWithColor(
					x1, y1,
					x2-x1+1, y2-y1+1,
					getColor()
			));
		} else {
			return Optional.empty();
		}
	}

	private OCImage targetImageCache;
	private long distTargetChangedTotal;

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		if (targetImageCache != targetImage) {
			targetImageCache = targetImage;
			distTargetChangedTotal = 0;

			for (int ty = y; ty < y+height; ty++) {
				for (int tx = x; tx < x + width; tx++) {
					int tb = targetImage.getBG(tx, ty);
					int tf = targetImage.getFG(tx, ty);
					int tq = targetImage.getQuadrant(tx, ty);

					long v = ImageUtils.getDistance(
							color, 0, 0,
							tb, tf, tq, targetImage.getPalette()
					);

					if (tq != 0 && tq != 255) {
						v *= 2;
					}

					distTargetChangedTotal += v;
				}
			}
		}

		boolean useFg = context.getCurrFg() == color;
		if (!useFg && context.getCurrBg() != color) {
			context.setCurrBg(color);
		}

		long v = 0;

		for (int ty = y; ty < y+height; ty++) {
			for (int tx = x; tx < x+width; tx++) {
				int tb = targetImage.getBG(tx, ty);
				int tf = targetImage.getFG(tx, ty);
				int tq = targetImage.getQuadrant(tx, ty);

				/* long distTargetChanged = ImageUtils.getDistance(
						color, 0, 0,
						tb, tf, tq, targetImage.getPalette()
				); */
				/* long distCurrentTarget = ImageUtils.getDistance(
						currImage.getBG(tx, ty),
						currImage.getFG(tx, ty),
						currImage.getQuadrant(tx, ty),
						tb, tf, tq, targetImage.getPalette()
				); */
				long distCurrentTarget = currTargetDelta.getDistance(tx, ty, targetImage);

				//v += (distCurrentTarget - distTargetChanged);
				v += distCurrentTarget;
			}
		}
		return v - distTargetChangedTotal;
	}

	@Override
	public int getCost() {
		return width > 1 && height > 1 ? 4 : 3;
	}

	@Override
	public int getCost(OCCommandContext context) {
		if (context.getCurrBg() == color || context.getCurrFg() == color) {
			return width > 1 && height > 1 ? 2 : 1;
		} else {
			return width > 1 && height > 1 ? 4 : 3;
		}
	}

	@Override
	public void apply(OCCommandContext context, OCImage image) {
		boolean useFg = context.getCurrFg() == color;
		if (!useFg && context.getCurrBg() != color) {
			context.setCurrBg(color);
		}

		int q = useFg ? 255 : 0;
		for (int ty = y; ty < y+height; ty++) {
			for (int tx = x; tx < x+width; tx++) {
				image.set(tx, ty, context.getCurrBg(), context.getCurrFg(), q);
			}
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		if (width == 1) {
			stream.write(0x19);
			stream.write(x);
			stream.write(y);
			stream.write(height);
			stream.write(color);
		} else if (height == 1) {
			stream.write(0x18);
			stream.write(x);
			stream.write(y);
			stream.write(width);
			stream.write(color);
		} else {
			stream.write(0x10);
			stream.write(x);
			stream.write(y);
			stream.write(width);
			stream.write(height);
			stream.write(color);
		}
	}

	@Override
	public Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.of(new Iterator(width));
	}

	public int getColor() {
		return color;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public class Iterator implements IntIterator {
		private final int cwidth;
		private int tx;
		private int ty;

		public Iterator(int width) {
			this.cwidth = width;
			tx = x;
			ty = y;
		}

		@Override
		public boolean hasNext() {
			return ty < (y+height-1) || tx < (x+width-1);
		}

		@Override
		public int nextInt() {
			int ttx = tx;
			int tty = ty;
			tx++;
			if (tx == x+width) {
				tx = x;
				ty++;
			}
			return tty * cwidth + ttx;
		}
	}
}
