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

import org.bytedeco.javacpp.*;

import java.awt.color.ColorSpace;
import java.awt.image.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.bytedeco.javacpp.avcodec.*;
import static org.bytedeco.javacpp.avformat.*;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.swscale.*;

@SuppressWarnings("deprecation")
public class VideoReaderFFMPEG implements IVideoReader {
	private final List<BufferedImage> frames;

	private final AVFormatContext formatContext;
	private final AVCodecContext videoContext;
	private final AVCodec videoCodec;
	private final AVFrame frame, frameRGB;
	private final AVPacket packet = new AVPacket();
	private final int videoId;
	private final BytePointer frameBuffer;
	private final SwsContext scaleContext;
	private final int width, height;
	private double framepos;
	private int framecount;
	private double frametime;

	public VideoReaderFFMPEG(File file, double framerate, Consumer<String> statusConsumer, Function<int[], int[]> sizeCalculator) throws IOException {
		formatContext = new AVFormatContext(null);

		statusConsumer.accept("Loading video file");

		av_register_all();
		ioError(avformat_open_input(formatContext, file.getAbsolutePath(), null, null), "avformat_open_input");
		ioError(avformat_find_stream_info(formatContext, (PointerPointer) null), "avformat_find_stream_info");

		int videoStreamId = -1;
		for (int i = 0; i < formatContext.nb_streams(); i++) {
			if (formatContext.streams(i).codec().codec_type() == AVMEDIA_TYPE_VIDEO) {
				videoStreamId = i;
				break;
			}
		}

		if (videoStreamId < 0) {
			throw new IOException("Could not find video stream!");
		}

		this.videoId = videoStreamId;

		videoContext = formatContext.streams(videoStreamId).codec();
		videoCodec = avcodec_find_decoder(videoContext.codec_id());
		if (videoCodec == null) {
			throw new IOException("Unsupported video codec!");
		}

		ioError(avcodec_open2(videoContext, videoCodec, (AVDictionary) null), "avcodec_open2 (video)");

		frame = av_frame_alloc();
		frameRGB = av_frame_alloc();
		if (frame == null || frameRGB == null) {
			throw new IOException("av_frame_alloc could not allocate memory!");
		}

		frameBuffer = new BytePointer(av_malloc(
				avpicture_get_size(AV_PIX_FMT_RGB24,
						videoContext.width(), videoContext.height())
		));

		int[] dims = new int[] { videoContext.width(), videoContext.height() };
		int[] dimsOut = sizeCalculator.apply(dims);
		width = dimsOut[0];
		height = dimsOut[1];

		scaleContext = sws_getContext(
				videoContext.width(), videoContext.height(),
				videoContext.pix_fmt(),
				width, height,
				AV_PIX_FMT_RGB24, SWS_FAST_BILINEAR,
				null, null, (DoublePointer) null
		);
		if (scaleContext == null) {
			throw new IOException("sws_getContext failed!");
		}

		avpicture_fill(new AVPicture(frameRGB), frameBuffer, AV_PIX_FMT_RGB24,
				videoContext.width(), videoContext.height());

		frames = new ArrayList<>();

		framepos = 0;
		frametime = 1 / framerate;
		framecount = 0;

		statusConsumer.accept("Reading video file");

		while (av_read_frame(formatContext, packet) >= 0) {
			if (packet.stream_index() == videoStreamId) {
				double pts = packet.pts() * av_q2d(formatContext.streams(videoStreamId).time_base());

				while (pts >= framepos) {
					framecount++;
					framepos += frametime;
				}
			}

			av_free_packet(packet);
		}

		av_seek_frame(formatContext, videoStreamId, 0, AVSEEK_FLAG_BACKWARD);
		framepos = 0;
	}

	@Override
	public int getFrameCount() {
		return framecount;
	}

	@Override
	public BufferedImage getFrame(int frameId) {
		int[] frameFinished = new int[1];
		while (frames.size() <= frameId) {
			if (av_read_frame(formatContext, packet) < 0) break;

			if (packet.stream_index() == videoId) {
				double pts = packet.pts() * av_q2d(formatContext.streams(videoId).time_base());
				avcodec_decode_video2(videoContext, frame, frameFinished, packet);

				if (frameFinished[0] != 0) {
					if (pts >= framepos) {
						sws_scale(scaleContext, frame.data(), frame.linesize(), 0,
								videoContext.height(), frameRGB.data(), frameRGB.linesize());

						BufferedImage image = getBufferedImage(frameRGB, width, height);

						while (pts >= framepos) {
							frames.add(image);
							framepos += frametime;
						}
					}
				}
			}

			av_free_packet(packet);
		}

		return frames.get(Math.min(frames.size() - 1, frameId));
	}

	private BufferedImage getBufferedImage(AVFrame f, int width, int height) {
		BytePointer data = f.data(0);
		int linesize = f.linesize(0);
		byte[] buf = new byte[width * height * 3];

		for (int y = 0; y < height; y++) {
			data = data.position(y * linesize);
			data.get(buf, y * width * 3, width * 3);
		}

		DataBuffer buffer = new DataBufferByte(buf, buf.length);
		WritableRaster raster = Raster.createInterleavedRaster(buffer,
				width, height, width * 3, 3, new int[] { 0, 1, 2 }, null);
		ColorSpace colorSpace = ColorSpace.getInstance(ColorSpace.CS_sRGB);
		ColorModel colorModel = new ComponentColorModel(colorSpace, false, true,
				ColorModel.OPAQUE, DataBuffer.TYPE_BYTE);
		return new BufferedImage(colorModel, raster, false, new Hashtable<>());
	}

	@Override
	public void finalize() {
		av_free(frameBuffer);
		av_free(frameRGB);
		av_free(frame);
		avcodec_close(videoContext);
		avformat_close_input(formatContext);
	}

	private void ioError(int v, String s) throws IOException {
		if (v != 0) {
			throw new IOException(s + ": error code " + v);
		}
	}
}
