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
import pl.asie.nadeshicodec.codec.nadeshiko.CodecNadeshiko;
import pl.asie.nadeshicodec.util.ImageUtils;

import java.util.Optional;

public class OCImageDelta {
	private final long[] data;

	public OCImageDelta(int width, int height) {
		data = new long[width * height];
	}

	public long getDistance(int x, int y, OCImage image) {
		return data[y*image.getWidthChars()+x];
	}

	private void recalc(OCImage currImage, OCImage targetImage, int x, int y) {
		data[y*targetImage.getWidthChars()+x] = ImageUtils.getDistance(
				currImage.getBG(x, y),
				currImage.getFG(x, y),
				currImage.getQuadrant(x, y),
				targetImage.getBG(x, y),
				targetImage.getFG(x, y),
				targetImage.getQuadrant(x, y),
				targetImage.getPalette()
		);
	}

	public void recalc(OCImage currImage, OCImage targetImage) {
		for (int y = 0; y < targetImage.getHeightChars(); y++) {
			for (int x = 0; x < targetImage.getWidthChars(); x++) {
				recalc(currImage, targetImage, x, y);
			}
		}
	}

	public void recalc(OCImage currImage, OCImage targetImage, IOCCommand command) {
		Optional<IntIterator> it = command.getChangedPositions(targetImage.getWidthChars(), targetImage.getHeightChars());
		if (it.isPresent()) {
			IntIterator i = it.get();
			while (i.hasNext()) {
				int v = i.nextInt();
				recalc(currImage, targetImage, v % targetImage.getWidthChars(), v / targetImage.getWidthChars());
			}
		}
	}
}
