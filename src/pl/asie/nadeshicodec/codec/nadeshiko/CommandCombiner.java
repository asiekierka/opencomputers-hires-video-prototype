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

package pl.asie.nadeshicodec.codec.nadeshiko;

import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.MathUtils;
import pl.asie.nadeshicodec.util.oc.*;

import java.util.*;
import java.util.stream.Collectors;

public class CommandCombiner {
	private static class Pair {
		private final long dist;
		private final int i;
		private final int j;
		private final int[] quad;

		public Pair(long dist, int i, int j, int[] quad) {
			this.dist = dist;
			this.i = i;
			this.j = j;
			this.quad = Arrays.copyOf(quad, quad.length);
		}
	}

	private static IOCCommand combineLosslessHelper(int[] bgColors, int[] fgColors, int[][] quadArrays, int[] xPos, int[] yPos, boolean vertical, int start, int end) {
		int len = 0;
		for (int i = start; i <= end; i++) {
			len += quadArrays[i].length;
		}

		if (bgColors[start] == fgColors[start]) {
			return new OCCommandFillWithColor(
					xPos[start], yPos[start], vertical ? 1 : len, vertical ? len : 1, bgColors[start]
			);
		} else {
			int[] q = new int[len];
			int pp = 0;
			for (int p = start; p <= end; p++) {
				for (int pq = 0; pq < quadArrays[p].length; pq++, pp++) {
					q[pp] = quadArrays[p][pq];
				}
			}
			return new OCCommandSetWithColor(
					xPos[start], yPos[start], q, bgColors[start], fgColors[start], vertical
			);
		}
	}

	public static Collection<CodecNadeshiko.OCCommandWeighted> combine(OCImage image, List<CodecNadeshiko.OCCommandWeighted> lineCmds, int start, int end, boolean vertical, int[] palette) {
		int size = end - start + 1;
		int[][] quadArrays = new int[size][];
		int[] bgColors = new int[size];
		int[] fgColors = new int[size];
		int[] xPos = new int[size];
		int[] yPos = new int[size];
		int[] pos = new int[size];
		int offset = 0;
		IntSet colors = new IntOpenHashSet();

		boolean hasDiff = false;

		for (int i = 0; i < size; i++) {
			IOCCommand setCmd = lineCmds.get(start + i).getLastCommand();
			if (setCmd instanceof OCCommandSetWithColor) {
				xPos[i] = ((OCCommandSetWithColor) setCmd).getX();
				yPos[i] = ((OCCommandSetWithColor) setCmd).getY();
				quadArrays[i] = ((OCCommandSetWithColor) setCmd).getQuadrantArray();
				bgColors[i] = ((OCCommandSetWithColor) setCmd).getBg();
				fgColors[i] = ((OCCommandSetWithColor) setCmd).getFg();
			} else if (setCmd instanceof OCCommandFillWithColor) {
				xPos[i] = ((OCCommandFillWithColor) setCmd).getX();
				yPos[i] = ((OCCommandFillWithColor) setCmd).getY();
				quadArrays[i] = new int[!vertical ? ((OCCommandFillWithColor) setCmd).getWidth() : ((OCCommandFillWithColor) setCmd).getHeight()];
				bgColors[i] = ((OCCommandFillWithColor) setCmd).getColor();
				fgColors[i] = bgColors[i];
			} else {
				return Collections.emptyList();
			}

			if (!hasDiff) {
				int iqx = xPos[i];
				int iqy = yPos[i];
				for (int iq = 0; iq < quadArrays[i].length; iq++) {
					if (ImageUtils.hasDistance(
						image.getBG(iqx, iqy),
						image.getFG(iqx, iqy),
						image.getQuadrant(iqx, iqy),
						bgColors[i],
						fgColors[i],
						quadArrays[i][iq]
					)) {
						hasDiff = true;
						break;
					}

					if (vertical) iqy++;
					else iqx++;
				}
			}

			pos[i] = vertical ? yPos[i] : xPos[i];
			colors.add(bgColors[i]);
			colors.add(fgColors[i]);
			//for (int a : OCUtils.getClosest(palette[bgColors[i]], palette))
			//	colors.add(a);
			//for (int a : OCUtils.getClosest(palette[fgColors[i]], palette))
			//	colors.add(a);
		}

		ImmutableList.Builder<CodecNadeshiko.OCCommandWeighted> builder = new ImmutableList.Builder<>();

		if (!hasDiff)
			return builder.build();

		// relativize pos
		offset = pos[0];
		for (int i = 0; i < pos.length; i++)
			pos[i] -= offset;
		int len = pos[pos.length - 1] + quadArrays[quadArrays.length - 1].length;

		// lossless combine
		if (size == 3 && bgColors[0] == bgColors[2] && fgColors[0] == fgColors[2]) {
			IOCCommand middleCmd = lineCmds.get(start + 1).getLastCommand();
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 2)
			));
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 2),
					middleCmd
			));
		} else if (size == 4 && bgColors[0] == bgColors[3] && fgColors[0] == fgColors[3]) {

			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 3)
			));
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 3),
					lineCmds.get(start + 1).getLastCommand(),
					lineCmds.get(start + 2).getLastCommand()
			));
		} else if (size == 5 && bgColors[0] == bgColors[4] && fgColors[0] == fgColors[4] && bgColors[1] == bgColors[3] && fgColors[1] == fgColors[3]) {
			//System.out.println("lossless5");
			IOCCommand middleCmd = lineCmds.get(start + 2).getLastCommand();
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 4)
			));
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 1, 3)
			));
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 4),
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 1, 3),
					middleCmd
			));
		/* } else if (size == 5 && bgColors[0] == bgColors[4] && fgColors[0] == fgColors[4]) {
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 4),
					lineCmds.get(start + 1).getLastCommand(),
					lineCmds.get(start + 2).getLastCommand(),
					lineCmds.get(start + 3).getLastCommand()
			)); */
		} else if (size == 7 && bgColors[0] == bgColors[6] && fgColors[0] == fgColors[6] && bgColors[1] == bgColors[5] && fgColors[1] == fgColors[5] && bgColors[2] == bgColors[4] && fgColors[2] == fgColors[4]) {
			IOCCommand middleCmd = lineCmds.get(start + 3).getLastCommand();
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 6),
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 1, 5),
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 2, 4),
					middleCmd
			));
		} else if (size == 8 && bgColors[0] == bgColors[7] && fgColors[0] == fgColors[7] && bgColors[1] == bgColors[3] && fgColors[1] == fgColors[3] && bgColors[4] == bgColors[6] && fgColors[4] == fgColors[6]) {
			builder.add(new CodecNadeshiko.OCCommandWeighted(
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 0, 7),
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 1, 3),
					combineLosslessHelper(bgColors, fgColors, quadArrays, xPos, yPos, vertical, 4, 6),
					lineCmds.get(start + 2).getLastCommand(),
					lineCmds.get(start + 5).getLastCommand()
			));
		}

		// gathered data, OK

		// get best color
		/* List<Pair> bestPairs = new ArrayList<>();
		int[] tmpArray = new int[len];
		int[] colorsA = colors.toIntArray();

		// j - bit set
		for (int i = 0; i < colorsA.length - 1; i++) {
			for (int j = i + 1; j < colorsA.length; j++) {
				long distance = 0;
				for (int p = 0; p < size; p++) {
					// should we use bg/bg, bg/fg, fg/bg or fg/fg?
					boolean useJforBG = ImageUtils.cheapPaletteDistance(j, bgColors[p], palette) < ImageUtils.cheapPaletteDistance(i, bgColors[p], palette);
					boolean useJforFG = ImageUtils.cheapPaletteDistance(j, fgColors[p], palette) < ImageUtils.cheapPaletteDistance(i, fgColors[p], palette);

					if (useJforBG == useJforFG) {
						int c = useJforBG ? 0xFF : 0x00;
						for (int px = 0; px < quadArrays[p].length; px++) {
							tmpArray[pos[p] + px] = c;
						}
					} else if (useJforBG) {
						for (int px = 0; px < quadArrays[p].length; px++) {
							tmpArray[pos[p] + px] = quadArrays[p][px] ^ 0xFF;
						}
					} else {
						for (int px = 0; px < quadArrays[p].length; px++) {
							tmpArray[pos[p] + px] = quadArrays[p][px];
						}
					}

					for (int px = 0; px < quadArrays[p].length; px++) {
						distance += ImageUtils.getDistance(
								i, j, tmpArray[pos[p] + px],
								bgColors[p], fgColors[p], quadArrays[p][px],
								palette
						);
					}
				}

				bestPairs.add(new Pair(distance, i, j, tmpArray));
			}
		}

		List<CodecNadeshiko.OCCommandWeighted> out = bestPairs.stream().sorted(Comparator.comparingLong(a -> a.dist)).limit(1).map(
				(p) -> new CodecNadeshiko.OCCommandWeighted(new OCCommandSetWithColor(xPos[0], yPos[0], p.quad, p.i, p.j, vertical))
		).collect(Collectors.toList());

		return out; */

		if (colors.size() <= 6) {
			long bestDistance = ImageUtils.cheapColorDistance(0x101010, 0x282828) * len;
			int bestI = -1, bestJ = -1;
			int[] bestArray = new int[len];
			int[] tmpArray = new int[len];
			int[] colorsA = colors.toIntArray();

			// j - bit set
			for (int i = 0; i < colorsA.length - 1; i++) {
				for (int j = i + 1; j < colorsA.length; j++) {
					long distance = 0;
					for (int p = 0; p < size; p++) {
						// should we use bg/bg, bg/fg, fg/bg or fg/fg?
						boolean useJforBG = ImageUtils.cheapPaletteDistance(j, bgColors[p], palette) < ImageUtils.cheapPaletteDistance(i, bgColors[p], palette);
						boolean useJforFG = ImageUtils.cheapPaletteDistance(j, fgColors[p], palette) < ImageUtils.cheapPaletteDistance(i, fgColors[p], palette);
						int pp = pos[p];
						int[] q = quadArrays[p];

						if (useJforBG == useJforFG) {
							int c = useJforBG ? 0xFF : 0x00;
							for (int px = 0; px < q.length; px++) {
								tmpArray[pp + px] = c;
							}
						} else if (useJforBG) {
							for (int px = 0; px < q.length; px++) {
								tmpArray[pp + px] = q[px] ^ 0xFF;
							}
						} else {
							for (int px = 0; px < q.length; px++) {
								tmpArray[pp + px] = q[px];
							}
						}

						for (int px = 0; px < q.length; px++) {
							distance += ImageUtils.getDistance(
									i, j, tmpArray[pp + px],
									bgColors[p], fgColors[p], q[px],
									palette
							);

							if (distance >= bestDistance) break;
						}
						if (distance >= bestDistance) break;
					}

					if (distance < bestDistance) {
						bestDistance = distance;
						bestI = i;
						bestJ = j;
						System.arraycopy(tmpArray, 0, bestArray, 0, tmpArray.length);
					}
				}
			}

			if (bestI >= 0 && bestJ >= 0) {
				builder.add(
						new CodecNadeshiko.OCCommandWeighted(
								new OCCommandSetWithColor(
										xPos[0], yPos[0], bestArray, bestI, bestJ, vertical
								)
						)
				);
			}
		}

		return builder.build();
	}

	public static Optional<CodecNadeshiko.OCCommandWeighted> combineOld(List<CodecNadeshiko.OCCommandWeighted> lineCmds, int start, int end, boolean vertical, int[] palette) {
		int iSize = end - start + 1;

		Int2IntOpenHashMap bgCount = new Int2IntOpenHashMap();
		Int2IntOpenHashMap fgCount = new Int2IntOpenHashMap();

		bgCount.defaultReturnValue(0);
		fgCount.defaultReturnValue(0);

		int setSize = 0;

		bgCount.clear();
		fgCount.clear();
		boolean[] fillUsesFgCol = new boolean[iSize];
		int lastBgCol = -1;
		int lastFgCol = -1;

		int expectedNext = -1;

		for (int i = 0; i < iSize; i++) {
			IOCCommand setCmd = lineCmds.get(start + i).getLastCommand();
			int[] quadArray = null;
			int bgCol = -1, fgCol = -1;
			int currentNext = 0;

			if (setCmd instanceof OCCommandSet) {
				currentNext = vertical ? ((OCCommandSet) setCmd).getY() : ((OCCommandSet) setCmd).getX();
				quadArray = ((OCCommandSet) setCmd).getQuadrantArray();

				for (IOCCommand command : lineCmds.get(start + i).commands) {
					if (command instanceof OCCommandSetColor) {
						OCCommandSetColor c = (OCCommandSetColor) command;
						if (c.getType() == OCCommandSetColor.Type.BG) {
							bgCol = c.getColor();
						} else if (c.getType() == OCCommandSetColor.Type.FG) {
							fgCol = c.getColor();
						}
					}
				}

				if (lastBgCol == fgCol || lastFgCol == bgCol) {
					int t = fgCol;
					fgCol = bgCol;
					bgCol = t;

					int[] quadArrayOld = quadArray;
					quadArray = new int[quadArrayOld.length];
					for (int ia = 0; ia < quadArray.length; ia++) quadArray[ia] = quadArrayOld[ia] ^ 0xFF;
				}
			} else if (setCmd instanceof OCCommandSetWithColor) {
				currentNext = vertical ? ((OCCommandSetWithColor) setCmd).getY() : ((OCCommandSetWithColor) setCmd).getX();
				quadArray = ((OCCommandSetWithColor) setCmd).getQuadrantArray();
				bgCol = ((OCCommandSetWithColor) setCmd).getBg();
				fgCol = ((OCCommandSetWithColor) setCmd).getFg();

				if (lastBgCol == fgCol || lastFgCol == bgCol) {
					int t = fgCol;
					fgCol = bgCol;
					bgCol = t;

					int[] quadArrayOld = quadArray;
					quadArray = new int[quadArrayOld.length];
					for (int ia = 0; ia < quadArray.length; ia++) quadArray[ia] = quadArrayOld[ia] ^ 0xFF;
				}
			} else if (setCmd instanceof OCCommandFillWithColor) {
				currentNext = vertical ? ((OCCommandFillWithColor) setCmd).getY() : ((OCCommandFillWithColor) setCmd).getX();
				int col = ((OCCommandFillWithColor) setCmd).getColor();
				quadArray = new int[Math.max(((OCCommandFillWithColor) setCmd).getWidth(), ((OCCommandFillWithColor) setCmd).getHeight())];
				if (lastFgCol == col) {
					fgCol = col;
					fillUsesFgCol[i] = true;
					for (int ia = 0; ia < quadArray.length; ia++) quadArray[ia] = 0xFF;
				} else {
					bgCol = col;
				}
			} else {
				return Optional.empty();
			}

			if (expectedNext >= 0) {
				if (expectedNext > currentNext) {
					return Optional.empty();
				}
			}

			int setLen = quadArray.length;
			expectedNext = currentNext + setLen;

			setSize += setLen;

			if (bgCol >= 0) {
				for (int q : quadArray)
					bgCount.addTo(bgCol, 8 - MathUtils.setBits8(q));
				lastBgCol = bgCol;
			}

			if (fgCol >= 0) {
				for (int q : quadArray)
					fgCount.addTo(bgCol, MathUtils.setBits8(q));
				lastFgCol = fgCol;
			}
		}

		//int maxColors = Math.min(3, iSize);
		//if (fgCount.size() >= maxColors && bgCount.size() >= maxColors) {
		//	return Optional.empty();
		//}

		int bgVal = -1;
		int bgValC = 0;
		int bgTotal = 0;

		for (int i : bgCount.keySet()) {
			bgTotal += bgCount.get(i);
			if (bgValC < bgCount.get(i)) {
				bgVal = i;
				bgValC = bgCount.get(i);
			}
		}

		int fgVal = bgVal;
		int fgValC = 0;
		int fgTotal = 0;

		for (int i : fgCount.keySet()) {
			fgTotal += fgCount.get(i);
			if (i != bgVal && fgValC < fgCount.get(i)) {
				fgVal = i;
				fgValC = fgCount.get(i);
			}
		}

		//if (fgVal == bgVal || bgVal == -1 || fgVal == -1)
		//	return Optional.empty();

		double bgFactor = bgValC / (double) bgTotal;
		double fgFactor = fgValC / (double) fgTotal;

		//if (bgFactor < 0.7 && fgFactor < 0.7)
		//	return Optional.empty();

		int[] combinedQs = new int[setSize];
		int i = 0;
		boolean hadZero = false;
		int xZero = 0;
		int yZero = 0;
		for (int is = 0; is < iSize; is++) {
			IOCCommand setCmd = lineCmds.get(start + is).getLastCommand();
			int[] qsa = null;

			if (setCmd instanceof OCCommandSet) {
				if (!hadZero) {
					xZero = ((OCCommandSet) setCmd).getX();
					yZero = ((OCCommandSet) setCmd).getY();
					hadZero = true;
				}
				qsa = ((OCCommandSet) setCmd).getQuadrantArray();
			} else if (setCmd instanceof OCCommandSetWithColor) {
				if (!hadZero) {
					xZero = ((OCCommandSetWithColor) setCmd).getX();
					yZero = ((OCCommandSetWithColor) setCmd).getY();
					hadZero = true;
				}
				qsa = ((OCCommandSetWithColor) setCmd).getQuadrantArray();
			} else if (setCmd instanceof OCCommandFillWithColor) {
				if (!hadZero) {
					xZero = ((OCCommandFillWithColor) setCmd).getX();
					yZero = ((OCCommandFillWithColor) setCmd).getY();
					hadZero = true;
				}
				qsa = new int[Math.max(((OCCommandFillWithColor) setCmd).getWidth(), ((OCCommandFillWithColor) setCmd).getHeight())];
				if (fillUsesFgCol[is]) {
					for (int ia = 0; ia < qsa.length; ia++) qsa[ia] = 0xFF;
				}
			}

			int len = qsa.length;
			System.arraycopy(qsa, 0, combinedQs, i, len);
			i += len;
		}

		OCCommandSetWithColor cmd = new OCCommandSetWithColor(
				xZero, yZero, combinedQs, bgVal, fgVal, vertical
		);
		return Optional.of(new CodecNadeshiko.OCCommandWeighted(cmd));
	}
}
