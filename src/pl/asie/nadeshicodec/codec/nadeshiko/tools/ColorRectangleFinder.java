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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.w3c.dom.css.Rect;
import pl.asie.nadeshicodec.codec.nadeshiko.CodecNadeshiko;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.MathUtils;
import pl.asie.nadeshicodec.util.oc.OCCommandFillWithColor;
import pl.asie.nadeshicodec.util.oc.OCImage;
import pl.asie.nadeshicodec.util.oc.OCUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class ColorRectangleFinder {
	public static class Rectangle {
		private final int x, y, width, height;
		private final int color;

		public Rectangle(int x, int y, int width, int height, int color) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.color = color;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		public int getWidth() {
			return width;
		}

		public int getHeight() {
			return height;
		}

		public int getColor() {
			return color;
		}

		public CodecNadeshiko.OCCommandWeighted toWCommand() {
			return new CodecNadeshiko.OCCommandWeighted(
					new OCCommandFillWithColor(
							x, y, width, height, color
					)
			);
		}

		public int hashCode() {
			return Objects.hash(x, y, width, height, color);
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Rectangle)) {
				return false;
			} else {
				Rectangle r = (Rectangle) o;
				return r.x == x && r.y == y && r.width == width && r.height == height && r.color == color;
			}
		}
	}

	private static void setColor(Int2ObjectMap<int[]> rectArrays, int ix, int iy, int width, int height, int color, int v) {
		if (v <= 0) return;

		int ip = iy * width + ix;
		int[] arr = rectArrays.computeIfAbsent(color, (x) -> new int[width * height]);
		if (arr[ip] < v) arr[ip] = v;
	}

	private static final ObjectPool<int[]> HISTOGRAM_INT_ARRAYS = new GenericObjectPool<int[]>(new BasePooledObjectFactory<int[]>() {
		@Override
		public int[] create() throws Exception {
			return new int[160*50];
		}

		@Override
		public PooledObject<int[]> wrap(int[] ints) {
			return new DefaultPooledObject<>(ints);
		}
	});

	public static Collection<Rectangle> getRectangles(OCImage image, OCImage lastImage) {
		final Int2ObjectMap<int[]> rectArrays = new Int2ObjectOpenHashMap<>();
		final int width = image.getWidthChars();

		for (int iy = 0; iy < image.getHeightChars(); iy++) {
			for (int ix = 0; ix < width; ix++) {
				int bg = image.getBG(ix, iy);
				int fg = image.getFG(ix, iy);
				int q = image.getQuadrant(ix, iy);
				int fgVal = MathUtils.setBits8(q);
				int bgVal = 8 - fgVal;
				if (q != 0 && q != 255 && !ImageUtils.hasDistance(
						bg, fg, q,
						lastImage.getBG(ix, iy), lastImage.getFG(ix, iy), lastImage.getQuadrant(ix, iy)
				)) {
					fgVal -= 2;
					bgVal -= 2;
				}

				setColor(rectArrays, ix, iy, width, image.getHeightChars(), bg, bgVal);
				setColor(rectArrays, ix, iy, width, image.getHeightChars(), fg, fgVal);
/*
				int[] bgCl = OCUtils.getClosest(bg, image.getPalette());
				int[] fgCl = OCUtils.getClosest(fg, image.getPalette());

				setColor(rectArrays, ix, iy, width, image.getHeightChars(), bgCl[1], bgVal/2);
				setColor(rectArrays, ix, iy, width, image.getHeightChars(), fgCl[1], fgVal/2);
				setColor(rectArrays, ix, iy, width, image.getHeightChars(), bgCl[2], (bgVal+2)/4);
				setColor(rectArrays, ix, iy, width, image.getHeightChars(), fgCl[2], (fgVal+2)/4); */
			}
		}

		double[] ratios = new double[]{ 0.1f, 0.3f };
		Set<Rectangle> rects = ConcurrentHashMap.newKeySet(ratios.length);

		DoubleStream.of(ratios).parallel().forEach((rv) -> {
			// histograms
			//int[] arr = new int[image.getWidthChars()*image.getHeightChars()];
			try {
				int[] arr = HISTOGRAM_INT_ARRAYS.borrowObject();

				for (int col : rectArrays.keySet()) {
					int[] arr0 = rectArrays.get(col);

					// smoothen out
					int ip = 0;
					for (int iy = 0; iy < image.getHeightChars(); iy++) {
						for (int ix = 0; ix < width; ix++, ip++) {
							float ratio = 0;
							int ratioDiv = 0;

							for (int isy = -1; isy <= 1; isy++) {
								int asy = iy + isy;
								if (asy >= image.getHeightChars()) {
									break;
								} else if (asy >= 0) {
									for (int isx = -2; isx <= 2; isx++) {
										int asx = ix + isx;
										if (asx >= image.getWidthChars()) {
											break;
										} else if (asx >= 0) {
											ratio += arr0[asy * width + asx];
											ratioDiv++;
										}
									}
								}
							}

							ratio = ratio / ratioDiv / 8;
							if (ratio >= rv) {
								if (iy == 0) {
									arr[ip] = 1;
								} else {
									arr[ip] = 1 + arr[ip - width];
								}
							} else {
								arr[ip] = 0;
							}
						}
					}

					ip = 0;
					// we can skip rows 0 and 1 as we're looking for fh >= 3
					for (int iy = 2; iy < image.getHeightChars(); iy++) {
						for (int ix = 0; ix < width; ix++, ip++) {
							if (arr[ip] != 0 && ((iy == image.getHeightChars() - 1) || arr[ip + width] == 0)) {
								int height = arr[ip];
								int x = ix;
								while (x < width && arr[ip - ix + x] >= height) {
									x++;
								}

								int fx = ix;
								int fy = (iy - height + 1);
								int fw = (x - ix);
								int fh = ((iy + 1)) - fy;

								if (fw >= 3 && fh >= 3) {
									rects.add(new Rectangle(
											fx, fy, fw, fh, col
									));
									//System.out.println("found rectangle " + fx + " " + fy + " " + fw + " " + fh);

									int ip2 = fy * width + fx;
									for (int i = 1; i < fw; i++, ip2++) {
										if (arr[ip2] == height) {
											arr[ip2] = 0;
										}
									}

							/* while (ix < width && arr[iy * width + ix] == height) {
								ix++;
								ip++;
							} */
								}
							}
						}
					}
				}

				HISTOGRAM_INT_ARRAYS.returnObject(arr);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		return rects;
	}
}
