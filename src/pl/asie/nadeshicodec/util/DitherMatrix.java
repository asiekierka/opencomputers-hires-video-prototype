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

public class DitherMatrix {
	private final int[] matrix;
	private final int width;

	public DitherMatrix() {
		this(new int[0]);
	}

	public DitherMatrix(int[] data) {
		matrix = data;
		width = (data.length <= 0 ? 0 : (int) Math.sqrt(data.length));
		assert width*width == data.length;
	}

	public int[] getMatrix() {
		return matrix;
	}

	public int getWidth() {
		return width;
	}

	public boolean isEnabled() {
		return width > 0;
	}
}
