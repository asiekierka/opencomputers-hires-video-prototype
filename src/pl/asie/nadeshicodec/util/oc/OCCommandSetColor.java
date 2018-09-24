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

public class OCCommandSetColor implements IOCCommand {
	public enum Type {
		BG,
		FG;
	}
	
	private final Type type;
	private final int color;

	public OCCommandSetColor(Type type, int color) {
		this.type = type;
		this.color = color;
	}

	@Override
	public double applyDistance(OCCommandContext context, OCImage currImage, OCImage targetImage, OCImageDelta currTargetDelta) {
		apply(context, null);
		return 0;
	}

	@Override
	public int getCost() {
		return 2;
	}

	public Type getType() {
		return type;
	}

	public int getColor() {
		return color;
	}

	@Override
	public int getCost(OCCommandContext context) {
		if (type == Type.BG && context.getCurrBg() == color) {
			return 0;
		} else if (type == Type.FG && context.getCurrBg() == color) {
			return 0;
		} else {
			return 2;
		}
	}

	@Override
	public void apply(OCCommandContext context, OCImage image) {
		if (type == Type.FG) {
			context.setCurrFg(color & 0xFF);
		} else {
			context.setCurrBg(color & 0xFF);
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		stream.write(type == Type.FG ? 0x03 : 0x02);
		stream.write(color);
	}
}
