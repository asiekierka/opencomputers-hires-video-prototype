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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;

public interface ICodec extends IVideoReader {
	void addFrame(BufferedImage input);
	default BufferedImage getPreprocessedFrame(IVideoReader reader, int image) {
		return reader.getFrame(image);
	}
	void write(OutputStream stream) throws IOException;
}
