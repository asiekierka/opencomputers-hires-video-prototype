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

public class OCCommandFill implements IOCCommand {
	private final int x, y;
	private final int width, height;
	private final boolean fg;

	public OCCommandFill(int x, int y, int width, int height, boolean fg) {
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
		this.fg = fg;
	}

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		double v = 0;
		for (int ty = y; ty < y+height; ty++) {
			for (int tx = x; tx < x + width; tx++) {
				double distTargetChanged = ImageUtils.getDistance(
						context.getCurrBg(),
						context.getCurrFg(),
						fg ? 255 : 0,
						targetImage.getBG(tx, ty),
						targetImage.getFG(tx, ty),
						targetImage.getQuadrant(tx, ty),
						targetImage.getPalette()
				);
				double distCurrentTarget = ImageUtils.getDistance(
						currImage.getBG(tx, ty),
						currImage.getFG(tx, ty),
						currImage.getQuadrant(tx, ty),
						targetImage.getBG(tx, ty),
						targetImage.getFG(tx, ty),
						targetImage.getQuadrant(tx, ty),
						targetImage.getPalette()
				);

				double vv = (distCurrentTarget - distTargetChanged);
				//if (vv > 0) vv /= 2;
				//else vv *= 2;
				v += vv;
			}
		}
		return v * 0.7;
	}

	@Override
	public int getCost() {
		return 2;
	}

	@Override
	public void apply(OCCommandContext context, OCImage image) {
		for (int ty = y; ty < y+height; ty++) {
			for (int tx = x; tx < x + width; tx++) {
				image.set(tx, ty, context.getCurrBg(), context.getCurrFg(), fg ? 255 : 0);
			}
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		stream.write(fg ? 0x07 : 0x06);
		stream.write(x);
		stream.write(y);
		stream.write(width);
		stream.write(height);
	}

	@Override
	public Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.of(new Iterator(width));
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
