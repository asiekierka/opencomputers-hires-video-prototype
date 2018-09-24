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

package pl.asie.nadeshicodec.codec.nadeshiko.tools;

import it.unimi.dsi.fastutil.ints.*;
import pl.asie.nadeshicodec.codec.nadeshiko.CodecNadeshiko;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.MathUtils;
import pl.asie.nadeshicodec.util.oc.OCCommandFillWithColor;
import pl.asie.nadeshicodec.util.oc.OCImage;
import pl.asie.nadeshicodec.util.oc.OCUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ColorRectangleFinderOld {
	private static void setColor(Int2ObjectMap<int[]> rectArrays, int ix, int iy, int width, int height, int color, int v) {
		if (v <= 0) return;

		int ip = iy * width + ix;
		int[] arr = rectArrays.computeIfAbsent(color, (x) -> new int[width * height]);
		if (arr[ip] < v) arr[ip] = v;
	}

	public static Collection<ColorRectangleFinder.Rectangle> getRectangles(OCImage image, OCImage lastImage) {
		Set<ColorRectangleFinder.Rectangle> rects = new HashSet<>();

		int[] blockSizes = new int[] { image.getHeightChars(), image.getHeightChars()/2 };
		for (Set<ColorRectangleFinder.Rectangle> ocwist : IntStream.of(blockSizes).parallel().mapToObj((blockSize) -> {
			Set<ColorRectangleFinder.Rectangle> wList = new HashSet<>();

			int iyMax = image.getHeightChars() - (blockSize-1);
			int ixMax = image.getWidthChars() - (blockSize-1);

			int areaW = (ixMax / blockSize) + 1;
			int areaH = (iyMax / blockSize) + 1;
			int[] area = new int[areaW * areaH];
			IntSet colors = new IntOpenHashSet();

			Int2IntOpenHashMap map = new Int2IntOpenHashMap();
			map.defaultReturnValue(0);

			for (int iy = 0; iy < iyMax; iy += blockSize) {
				for (int ix = 0; ix < ixMax; ix += blockSize) {
					map.clear();

					boolean hasNonTrivial = false;
					boolean hasChange = false;

					for (int iyp = iy; iyp < iy + blockSize; iyp++) {
						for (int ixp = ix; ixp < ix + blockSize; ixp++) {
							int iq = image.getQuadrant(ixp, iyp);
							hasNonTrivial |= (iq != 0 && iq != 255);
							hasChange |= (ImageUtils.hasDistance(
									image.getBG(ixp, iyp),
									image.getFG(ixp, iyp),
									image.getQuadrant(ixp, iyp),
									lastImage.getBG(ixp, iyp),
									lastImage.getFG(ixp, iyp),
									lastImage.getQuadrant(ixp, iyp)
							));
							int ib = MathUtils.setBits8(iq);
							map.addTo(image.getFG(ixp, iyp), ib);
							map.addTo(image.getBG(ixp, iyp), 8 - ib);
						}
					}

					if (!hasChange && hasNonTrivial) {
						continue;
					}

					int maxCol = -1;
					int maxColC = 0;
					for (int k : map.keySet()) {
						if (maxColC < map.get(k)) {
							maxCol = k;
							maxColC = map.get(k);
						}
					}
					if (maxColC < (blockSize*blockSize*4/4)) {
						area[(iy / blockSize) * areaW + (ix / blockSize)] = -1;
						continue;
					}

					area[(iy / blockSize) * areaW + (ix / blockSize)] = maxCol;
					colors.add(maxCol);
				}
			}

			int[] colArea = new int[areaW * areaH];
			for (int col : colors) {
				int ip = 0;

				// find all rectangles using histogram
				for (int iy = 0; iy < areaH; iy++) {
					for (int ix = 0; ix < areaW; ix++, ip++) {
						if (area[ip] == col) {
							colArea[ip] = 1 + (iy > 0 ? colArea[ip - areaW] : 0);
						} else {
							colArea[ip] = 0;
						}
					}
				}

				// add all interesting rectangles
				// TODO: cull smaller instances later (say, 6x2, 5x2, 4x2, ...)
				for (int iy = 0; iy < areaH; iy++) {
					for (int ix = 0; ix < areaW; ix++) {
						ip = iy * areaW + ix;
						if (colArea[ip] != 0 && ((iy >= areaH - 1) || colArea[ip + areaW] == 0)) {
							int height = colArea[ip];
							int x = ix;
							while (x < areaW && colArea[ip - ix + x] >= height) {
								x++;
							}

							int fx = ix * blockSize;
							int fy = (iy - height + 1) * blockSize;
							int fw = (x - ix) * blockSize;
							int fh = ((iy + 1) * blockSize) - fy;

							int lastBlockSize = blockSize * 2;
							if (fx % lastBlockSize == 0 && fy % lastBlockSize == 0 && fw % lastBlockSize == 0
									&& fh % lastBlockSize == 0) {
								continue;
							}
							lastBlockSize = blockSize * 3;
							if (fx % lastBlockSize == 0 && fy % lastBlockSize == 0 && fw % lastBlockSize == 0
									&& fh % lastBlockSize == 0) {
								continue;
							}
							lastBlockSize = blockSize * 5;
							if (fx % lastBlockSize == 0 && fy % lastBlockSize == 0 && fw % lastBlockSize == 0
									&& fh % lastBlockSize == 0) {
								continue;
							}

							if (fw >= 3 && fh >= 3) {
								wList.add(new ColorRectangleFinder.Rectangle(
										fx, fy, fw, fh, col
								));
								//System.out.println("found rectangle " + fx + " " + fy + " " + fw + " " + fh);

								int ip2 = iy * areaW + ix;
								for (int i = 1; i < (x - ix); i++, ip2++) {
									if (colArea[ip2] == height) {
										colArea[ip2] = 0;
									}
								}
							}
						}
					}
				}

			}

			return wList;
		}).collect(Collectors.toList())) {
			rects.addAll(ocwist);
		}

		return rects;
	}
}
