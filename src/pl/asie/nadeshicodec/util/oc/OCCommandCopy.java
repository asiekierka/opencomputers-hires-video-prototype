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

public class OCCommandCopy implements IOCCommand {
	private final int x, y;
	private final int tx, ty;
	private final int width, height;

	public OCCommandCopy(int x, int y, int tx, int ty, int width, int height) {
		this.x = x;
		this.y = y;
		this.tx = tx;
		this.ty = ty;
		this.width = width;
		this.height = height;
	}

	public double getCopyDiff(OCImage from, OCImage to){
		double v = 0;
		for (int py = y+ty; py < y+ty + height; py++) {
			for (int px = x + tx; px < x + tx + width; px++) {
				// v += (difference between copied and new) - (difference between old and new)
				// negative difference - old diff > copied diff, copying makes sense
				v += ImageUtils.getDistance(
						from.getBG(px - tx, py - ty),
						from.getFG(px - tx, py - ty),
						from.getQuadrant(px - tx, py - ty),
						to.getBG(px, py),
						to.getFG(px, py),
						to.getQuadrant(px, py),
						from.getPalette()
				) - ImageUtils.getDistance(
						from.getBG(px, py),
						from.getFG(px, py),
						from.getQuadrant(px, py),
						to.getBG(px, py),
						to.getFG(px, py),
						to.getQuadrant(px, py),
						from.getPalette()
				);
			}
		}
		return v;
	}

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		double v = 0;
		for (int py = y+ty; py < y+ty + height; py++) {
			for (int px = x+tx; px < x+tx + width; px++) {
				double distTargetChanged = ImageUtils.getDistance(
						targetImage.getBG(px-tx, py-ty),
						targetImage.getFG(px-tx, py-ty),
						targetImage.getQuadrant(px-tx, py-ty),
						targetImage.getBG(px, py),
						targetImage.getFG(px, py),
						targetImage.getQuadrant(px, py),
						targetImage.getPalette()
				);
				double distCurrentTarget = ImageUtils.getDistance(
						currImage.getBG(px, py),
						currImage.getFG(px, py),
						currImage.getQuadrant(px, py),
						targetImage.getBG(px, py),
						targetImage.getFG(px, py),
						targetImage.getQuadrant(px, py),
						targetImage.getPalette()
				);

				v += (distCurrentTarget - distTargetChanged);
			}
		}
		return v;
	}

	@Override
	public int getCost() {
		return 4;
	}

	@Override
	public void apply(OCCommandContext context, OCImage image) {
		OCImage imageOld = image.copy();

		for (int py = y; py < y+height; py++) {
			for (int px = x; px < x + width; px++) {
				image.set(px+tx, py+ty,
						imageOld.getBG(px, py),
						imageOld.getFG(px, py),
						imageOld.getQuadrant(px, py));
			}
		}
	}

	public void mark(OCImage image) {
		OCImage imageOld = image.copy();

		for (int py = y; py < y+height; py++) {
			for (int px = x; px < x + width; px++) {
				image.set(px+tx, py+ty,
						216,
						0,
						imageOld.getQuadrant(px, py));
			}
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		stream.write(0x09);
		stream.write(x);
		stream.write(y);
		stream.write(tx & 0xFF);
		stream.write(tx >> 8);
		stream.write(ty & 0xFF);
		stream.write(ty >> 8);
		stream.write(width);
		stream.write(height);
	}

	@Override
	public Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.empty(); // TODO
	}
}
