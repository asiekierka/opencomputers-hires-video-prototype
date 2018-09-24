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

import pl.asie.nadeshicodec.util.DitherMatrix;
import pl.asie.nadeshicodec.util.oc.OCUtils;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.function.Consumer;

public class CodecManager {
	private IVideoReader reader;
	private ICodec codec;
	private boolean showOCInput;

	public CodecManager() {
		showOCInput = true;
	}

	public void setReaderCodec(IVideoReader reader, ICodec codec) {
		this.reader = reader;
		setCodec(codec);
	}

	public void setCodec(ICodec codec) {
		this.codec = codec;
		if (this.codec == null) {
			throw new RuntimeException("Cannot have null codec!");
		}
	}

	public void write(File f) throws IOException {
		FileOutputStream stream = new FileOutputStream(f);
		codec.write(stream);
	}

	public int getFrameCount() {
		return reader != null ? reader.getFrameCount() : 0;
	}

	public BufferedImage getInputFrame(int frame) {
		return showOCInput ? codec.getPreprocessedFrame(reader, frame) : reader.getFrame(frame);
	}

	public BufferedImage getOutputFrame(int frame, Consumer<Integer> frameRenderConsumer) {
		while (codec.getFrameCount() <= frame) {
			long time = System.currentTimeMillis();
			frameRenderConsumer.accept(codec.getFrameCount() + 1);
			codec.addFrame(reader.getFrame(codec.getFrameCount()));
			System.out.println("Frame " + codec.getFrameCount() + " render time = " + (System.currentTimeMillis() - time) + " ms");
		}

		return codec.getFrame(frame);
	}
}
