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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * Command IDs:
 *
 * 0x01 - end frame
 * 0x02, 0x03 - set color (bg, fg): byte idx
 * 0x04, 0x05 - set (0x05 = vertical): byte x, byte y, byte width, byte... quadrants
 * 0x06, 0x07 - fill (0x07 = use fg); byte x, byte y, byte width, byte height
 * 0x08 - copy; byte x, byte y, short tx, short ty, byte width, byte height
 *
 * 0x10 - fill+color; byte x, byte y, byte width, byte height, byte color
 * 0x12 (0x13 = vertical) - set+color; byte x, byte y, byte width, byte bg, byte fg, byte... quadrants
 *
 * 0x18 - fill+color h=1, doesn't send w
 * 0x19 - fill+color w=1, doesn't send h
 *
 * 0x22 (0x23 = vertical) - set+color+RLE; quadrants are stored in a new form:
 * 0x01 - 0xA0: the following x values are quadrants
 * 0xA1 - 0xFF: the following value is the next quadrant repeated (x-0xB0) times
 * 0x00 - end
 *
 * no width
 *
 * the +color variants are clever as they can auto-invert
 */

public interface IOCCommand {
	double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta);
	int getCost();
	default int getCost(OCCommandContext context) {
		return getCost();
	}
	void apply(OCCommandContext context, OCImage image);
	default Optional<IntIterator> getChangedPositions(int width, int height) {
		return Optional.empty();
	}
	void write(OutputStream stream) throws IOException;
}
