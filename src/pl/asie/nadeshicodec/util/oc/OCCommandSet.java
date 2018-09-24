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

public class OCCommandSet implements IOCCommand {
	private final int x, y;
	private final int[] quadrants;
	private final boolean vertical;

	public OCCommandSet(int x, int y, int[] quadrants, boolean vertical) {
		this.x = x;
		this.y = y;
		this.quadrants = quadrants;
		this.vertical = vertical;
	}

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		int tx = x;
		int ty = y;
		double v = 0;
		for (int quadrant : quadrants) {
			double distTargetChanged = ImageUtils.getDistance(
					context.getCurrBg(),
					context.getCurrFg(),
					quadrant,
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

			v += distCurrentTarget - distTargetChanged;

			if (vertical) ty++;
			else tx++;
		}
		return v;
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
	public void apply(OCCommandContext context, OCImage image) {
		int tx = x;
		int ty = y;
		for (int quadrant : quadrants) {
			image.set(tx, ty, context.getCurrBg(), context.getCurrFg(), quadrant);
			if (vertical) ty++;
			else tx++;
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		stream.write(vertical ? 0x05 : 0x04);
		stream.write(x);
		stream.write(y);
		stream.write(quadrants.length);
		for (int i = 0; i < quadrants.length; i++) {
			stream.write(quadrants[i]);
		}
	}

	@Override
	public Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.of(new Iterator(width));
	}

	public int[] getQuadrantArray() {
		return quadrants;
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
