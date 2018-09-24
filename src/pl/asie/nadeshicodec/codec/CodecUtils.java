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

package pl.asie.nadeshicodec.codec;

public class CodecUtils {
	public static int[] scaleDimensionsDefault(int[] dims) {
		boolean ignoreAspectRatio = false;
		float x = (ignoreAspectRatio ? 1.6f : (float) dims[0] / dims[1]);
		float y = 1.0f;
		float a = Math.min(Math.min(
				(float) 320 / x,
				(float) 640 / y),
				(float) Math.sqrt((float) (320 * 200) / (x * y)));
		int w = (int) (x * a) & (~1);
		int h = (int) (y * a) & (~3);
		int rw = (int) (h * x) & (~1);
		if (rw < w) w = rw;
		System.out.println(w + " x " + h);
		return new int[]{w, h};
	}
}
