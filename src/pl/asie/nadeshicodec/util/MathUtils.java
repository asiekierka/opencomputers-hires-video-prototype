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

public class MathUtils {
	private static final int[] setBits;
	private static final float[] sqrts;

	static {
		setBits = new int[256];
		sqrts = new float[257];
		for (int i = 1; i <= 256; i++) {
			sqrts[i] = (float) Math.sqrt(i);
		}

		for (int i = 0; i < 16; i++) {
			int q = i;
			while (q != 0) {
				if ((q & 1) != 0) setBits[i]++;
				q >>= 1;
			}
		}
		for (int i = 16; i < 256; i++) {
			setBits[i] = (setBits[i >> 4]) + (setBits[i & 15]);
		}
	}

	public static int setBits8(int v) {
		return setBits[v];
	}

	public static float sqrt8(int i) {
		return sqrts[i];
	}
}
