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
import pl.asie.nadeshicodec.util.ImageUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

public class OCCommandSetWithColor implements IOCCommand {
	private final int x, y;
	private final int[] quadrants;
	private final int bg, fg;
	private final boolean vertical;

	public OCCommandSetWithColor(int x, int y, int[] quadrants, int bg, int fg, boolean vertical) {
		this.x = x;
		this.y = y;
		if (fg > bg) {
			int t = fg;
			fg = bg;
			bg = t;
			int[] qold = quadrants;
			quadrants = new int[qold.length];
			for (int i = 0; i < qold.length; i++) {
				quadrants[i] = qold[i] ^ 0xFF;
			}
		} else if (bg == fg) {
			throw new RuntimeException("Should not happen!");
		}

		this.quadrants = quadrants;
		this.bg = bg;
		this.fg = fg;
		this.vertical = vertical;
	}

	private int updateContext(OCCommandContext context, boolean simulate) {
		if (bg == context.getCurrBg() && fg == context.getCurrFg()) {
			return 0;
		} else if (bg == context.getCurrFg() && fg == context.getCurrBg()) {
			return 0;
		} else if (bg == context.getCurrBg()) {
			if (!simulate) context.setCurrFg(fg);
			return 2;
		} else if (fg == context.getCurrBg()) {
			if (!simulate) context.setCurrFg(bg);
			return 2;
		} else if (fg == context.getCurrFg()) {
			if (!simulate) context.setCurrBg(bg);
			return 2;
		} else if (bg == context.getCurrFg()) {
			if (!simulate) context.setCurrBg(fg);
			return 2;
		} else {
			if (!simulate) context.setCurrBg(bg);
			if (!simulate) context.setCurrFg(fg);
			return 4;
		}
	}

	private OCImage targetImageCache;
	private long distTargetChangedTotal;

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		if (targetImageCache != targetImage) {
			targetImageCache = targetImage;
			distTargetChangedTotal = 0;

			int tx = x;
			int ty = y;
			for (int quadrant : quadrants) {
				int tb = targetImage.getBG(tx, ty);
				int tf = targetImage.getFG(tx, ty);
				int tq = targetImage.getQuadrant(tx, ty);

				distTargetChangedTotal += ImageUtils.getDistance(
						bg, fg, quadrant,
						tb, tf, tq, targetImage.getPalette()
				);
			}

			if (vertical) ty++;
			else tx++;
		}
		updateContext(context, false);

		int tx = x;
		int ty = y;
		long v = 0;
		boolean hasChange = false;
		for (int quadrant : quadrants) {
			if (!hasChange) {
				hasChange = ImageUtils.hasDistance(
						bg, fg, quadrant,
						currImage.getBG(tx, ty),
						currImage.getFG(tx, ty),
						currImage.getQuadrant(tx, ty)
				);
			}
			/* long distTargetChanged = ImageUtils.getDistance(
					bg,
					fg,
					quadrant,
					targetImage.getBG(tx, ty),
					targetImage.getFG(tx, ty),
					targetImage.getQuadrant(tx, ty),
					targetImage.getPalette()
			); */
			/* long distCurrentTarget = ImageUtils.getDistance(
					currImage.getBG(tx, ty),
					currImage.getFG(tx, ty),
					currImage.getQuadrant(tx, ty),
					targetImage.getBG(tx, ty),
					targetImage.getFG(tx, ty),
					targetImage.getQuadrant(tx, ty),
					targetImage.getPalette()
			); */
			long distCurrentTarget = currTargetDelta.getDistance(tx, ty, targetImage);

			v += distCurrentTarget /* - distTargetChanged */;

			if (vertical) ty++;
			else tx++;
		}
		double res = !hasChange ? Double.MIN_VALUE : v - distTargetChangedTotal;
		return res;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public boolean isVertical() {
		return vertical;
	}

	@Override
	public int getCost() {
		return 1;
	}

	@Override
	public int getCost(OCCommandContext context) {
		return 1 + updateContext(context, true);
	}

	@Override
	public void apply(OCCommandContext context, OCImage image) {
		updateContext(context, false);

		int tx = x;
		int ty = y;
		for (int quadrant : quadrants) {
			image.set(tx, ty, bg, fg, quadrant);
			if (vertical) ty++;
			else tx++;
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		if (quadrants.length <= 4) {
			stream.write(vertical ? 0x13 : 0x12);
			stream.write(x);
			stream.write(y);
			stream.write(quadrants.length);
			stream.write(bg);
			stream.write(fg);
			for (int quadrant : quadrants) {
				stream.write(quadrant);
			}
			return;
		}

		stream.write(vertical ? 0x23 : 0x22);
		stream.write(x);
		stream.write(y);
		stream.write(bg);
		stream.write(fg);

		int rlePos = 0;
		int repeats = 0;
		int lastValue = -1;

		for (int i = 0; i < quadrants.length; i++) {
			boolean writeNow = false;
			if (quadrants[i] == lastValue) {
				repeats++;
				if (repeats == (0xFF - 0xA0)) {
					writeNow = true;
				}
			} else if (repeats >= 3) {
				writeNow = true;
			} else {
				repeats = 0;
			}

			if (writeNow) {
				// write
				int nonRepeats = (i - rlePos) - repeats;
				if (nonRepeats > 0) {
					stream.write(nonRepeats);
					for (int j = 0; j < nonRepeats; j++) {
						stream.write(quadrants[rlePos + j]);
					}
				}
				rlePos += nonRepeats;
				if (repeats > 0) {
					stream.write(0xA0 + repeats);
					stream.write(lastValue);
				}
				rlePos += repeats;

				repeats = 0;
			}

			lastValue = quadrants[i];
		}

		int nonRepeats = (quadrants.length - rlePos) - repeats;
		if (nonRepeats > 0) {
			stream.write(nonRepeats);
			for (int j = 0; j < nonRepeats; j++) {
				stream.write(quadrants[rlePos + j]);
			}
		}
		rlePos += nonRepeats;
		if (repeats > 0) {
			stream.write(0xA0 + repeats);
			stream.write(quadrants[rlePos]);
		}

		stream.write(0x00);
	}

	@Override
	public Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.of(new Iterator(width));
	}

	public int[] getQuadrantArray() {
		return quadrants;
	}

	public int getBg() {
		return bg;
	}

	public int getFg() {
		return fg;
	}

	public class Iterator implements IntIterator {
		private final int width;
		private int tx;
		private int ty;
		private int i;

		public Iterator(int width) {
			this.width = width;
			tx = x;
			ty = y;
			i = 0;
		}

		@Override
		public boolean hasNext() {
			return i < quadrants.length;
		}

		@Override
		public int nextInt() {
			int ttx = tx;
			int tty = ty;
			if (vertical) {
				ty++;
			} else {
				tx++;
			}
			i++;
			return tty * width + ttx;
		}
	}
}
