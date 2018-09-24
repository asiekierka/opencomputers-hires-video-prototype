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
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.*;
import pl.asie.nadeshicodec.codec.ICodec;
import pl.asie.nadeshicodec.codec.IVideoReader;
import pl.asie.nadeshicodec.codec.nadeshiko.tools.ColorRectangleFinder;
import pl.asie.nadeshicodec.codec.nadeshiko.tools.ColorRectangleFinderOld;
import pl.asie.nadeshicodec.util.DitherMatrix;
import pl.asie.nadeshicodec.util.ImageUtils;
import pl.asie.nadeshicodec.util.MathUtils;
import pl.asie.nadeshicodec.util.oc.*;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class CodecNadeshiko implements ICodec {
	public static class OCCommandWeighted {
		public final List<IOCCommand> commands;
		private int weightCacheCid = -1;
		private double weightCache;
		private int tccCacheCid = -1;
		private int tccCache;
		private boolean used;
//		private int lcCacheCid = -1;
//		private int lcCache;

		public OCCommandWeighted(IOCCommand... commands) {
			this.commands = Arrays.asList(commands);
		}

		public OCCommandWeighted(ImmutableList.Builder<IOCCommand> commands) {
			this.commands = commands.build();
		}

		public OCCommandWeighted(Collection<IOCCommand> commands) {
			this.commands = ImmutableList.copyOf(commands);
		}

		public IOCCommand getLastCommand() {
			return commands.get(commands.size() - 1);
		}

		public int getTotalCommandCost(OCCommandContext context) {
			if (context.getContextId() != tccCacheCid) {
				int s = 0;
				for (IOCCommand command : commands) s += command.getCost(context);
				tccCacheCid = context.getContextId();
				tccCache = s;
			}
			return tccCache;
		}

		public double getWeightedValue(OCCommandContext context, OCImage image, OCImage perfectImage, OCImageDelta delta, int frameNumber) {
			if (used) {
				return Double.NEGATIVE_INFINITY;
			}

			/* if (lcCacheCid != frameNumber) {
				int lcMax = 0;

				for (IOCCommand c : commands) {
					Optional<IntIterator> iterator = c.getChangedPositions(160, 160);
					if (iterator.isPresent()) {
						IntIterator it = iterator.get();
						while (it.hasNext()) {
							int i = it.nextInt();

							int diff = frameNumber - lastChange[i];
							if (lcMax < diff) {
								lcMax = diff;
							}
						}
					}
				}

				lcCache = lcMax;
				lcCacheCid = frameNumber;
			} */

			if (weightCacheCid != (frameNumber * 1024) + context.getContextId()) {
				OCCommandContext changedCtx = context.copy(false);

				double change = 0;
				for (IOCCommand c : commands) {
					change += c.applyDistance(changedCtx, image, perfectImage, delta);
				}

				//weightCache = change * (lcCache+1);
				weightCache = change;
				weightCacheCid = (frameNumber * 1024) + context.getContextId();
			}

			return weightCache;
		}
	}

	private final List<OCImage> imageList = new ArrayList<>();
	private final List<IOCCommand> commands = new ArrayList<>();
	private final DitherMatrix ditherMatrix;
	private OCCommandContext lastContext = new OCCommandContext();

	public CodecNadeshiko() {
		this(new DitherMatrix());
	}

	public CodecNadeshiko(DitherMatrix ditherMatrix) {
		this.ditherMatrix = ditherMatrix;
	}

	@Override
	public int getFrameCount() {
		return imageList.size();
	}

	private void iterate(OCImage lastImage, OCImage image, OCImageDelta delta, List<OCCommandWeighted> weighteds, boolean vertical, boolean calcSublines) {
		IntList qs = new IntArrayList();

		for (int ay = 0; ay < (vertical ? image.getWidthChars() : image.getHeightChars()); ay++) {
			int qx = 0;
			int qy = 0;
			int currBg = -1;
			int currFg = -1;
			boolean usesBg = false;
			boolean usesFg = false;
			boolean hasMismatch = false;

			List<OCCommandWeighted> lineCmds = new ArrayList<>();
			List<OCCommandWeighted> lineCmdsToAdd = new ArrayList<>();

			for (int ax = 0; ax < (vertical ? image.getHeightChars() : image.getWidthChars()); ax++) {
				int x = vertical ? ay : ax;
				int y = vertical ? ax : ay;

				int bg = image.getBG(x, y);
				int fg = image.getFG(x, y);
				int q = image.getQuadrant(x, y);

				// colors don't match
				boolean tileColorMismatch = (q != 255 && currBg != bg) || (q != 0 && currFg != fg);
				if (tileColorMismatch) {
					if ((q == 0 || currBg == fg) && (q == 255 || currFg == bg)) {
						int t = bg;
						bg = fg;
						fg = t;
						q ^= 0xFF;
						tileColorMismatch = (q != 255 && currBg != bg) || (q != 0 && currFg != fg);
						if (tileColorMismatch) {
							throw new RuntimeException("Shouldn't happen!");
						}
					}
				}

				// this quadrant != previous quadrant
				boolean tileMismatch = ImageUtils.hasDistance(
						bg, fg, q,
						lastImage.getBG(x, y),
						lastImage.getFG(x, y),
						lastImage.getQuadrant(x, y)
				);

				// if the colors don't match, we need to save the command
				if ((tileColorMismatch || (!hasMismatch && tileMismatch)) && !qs.isEmpty()) {
					if (!lineCmds.isEmpty() || hasMismatch) {
						if (!usesFg) {
							if (vertical) {
								lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, 1, qs.size(), currBg)));
							} else {
								lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, qs.size(), 1, currBg)));
							}
						} else if (!usesBg) {
							if (vertical) {
								lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, 1, qs.size(), currFg)));
							} else {
								lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, qs.size(), 1, currFg)));
							}
						} else {
							int[] qs1 = qs.toIntArray();
							lineCmds.add(new OCCommandWeighted(
									new OCCommandSetWithColor(qx, qy, qs1, currBg, currFg, vertical)
							));
						}
						// Skip 1x1 blocks during vertical scan
						// if !vertical, lineCmdsToAdd == lineCmds
						if ((!vertical || qs.size() > 1) && hasMismatch && (!lineCmdsToAdd.isEmpty() || tileMismatch)) {
							lineCmdsToAdd.add(lineCmds.get(lineCmds.size() - 1));
						}
					}
					qs.clear();
				}

				// if the tile is mismatched or we're already adding...
				if (qs.isEmpty()) {
					qx = x;
					qy = y;
					currBg = bg;
					currFg = fg;
					usesBg = false;
					usesFg = false;
					hasMismatch = false;
				}
				qs.add(q);
				hasMismatch |= tileMismatch;
				usesBg |= q != 255;
				usesFg |= q != 0;
			}

			if (!qs.isEmpty()) {
				if (!lineCmds.isEmpty() || hasMismatch) {
					if (!usesFg) {
						if (vertical) {
							lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, 1, qs.size(), currBg)));
						} else {
							lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, qs.size(), 1, currBg)));
						}
					} else if (!usesBg) {
						if (vertical) {
							lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, 1, qs.size(), currFg)));
						} else {
							lineCmds.add(new OCCommandWeighted(new OCCommandFillWithColor(qx, qy, qs.size(), 1, currFg)));
						}
					} else {
						int[] qs1 = qs.toIntArray();
						lineCmds.add(new OCCommandWeighted(
								new OCCommandSetWithColor(qx, qy, qs1, currBg, currFg, vertical)
						));
					}
					// Skip 1x1 blocks during vertical scan
					// if !vertical, lineCmdsToAdd == lineCmds
					if ((!vertical || qs.size() > 1) && hasMismatch) {
						lineCmdsToAdd.add(lineCmds.get(lineCmds.size() - 1));
					}
				}

				qs.clear();
			}

			// Trim
			while (lineCmds.size() > 0) {
				OCCommandWeighted lastLineCmd = lineCmds.get(lineCmds.size() - 1);
				double dist = lastLineCmd.getLastCommand().applyDistance(new OCCommandContext(), lastImage, image, delta);
				if (dist < 1E-10) {
					lineCmds.remove(lineCmds.size() - 1);
				} else {
					break;
				}
			}

			weighteds.addAll(lineCmdsToAdd);

			// combine
			if (calcSublines) {
				//for (List<OCCommandWeighted> ocwist : IntStream.rangeClosed(3, Math.min(11, lineCmds.size())).parallel().mapToObj((iSize) -> {
				for (List<OCCommandWeighted> ocwist : IntStream.of(3, lineCmds.size()).parallel().mapToObj((iSize) -> {
					List<OCCommandWeighted> cmds = new ArrayList<>();
					if (iSize > lineCmds.size()) return cmds;

					for (int iStart = 0; iStart < lineCmds.size() + 1 - iSize; iStart++) {
						//if (ocw.getLastCommand().applyDistance(new OCCommandContext(), lastImage, image) >= -1E-10) {
						//}
						cmds.addAll(CommandCombiner.combine(image, lineCmds, iStart, iStart + iSize - 1, vertical, image.getPalette()));
					}

					return cmds;
				}).collect(Collectors.toList())) {
					weighteds.addAll(ocwist);
				}
			}
		}
	}

	private OCImage lastUneditedImage = null;
	private OCImage lastImage = null;
	private double totalDiff = 0;

	@Override
	public void addFrame(BufferedImage input) {
		final int frameNumber = imageList.size();
		OCImage image = OCUtils.from(input, OCUtils.getPaletteTier3(), ditherMatrix);

		if (imageList.isEmpty()) {
			imageList.add(image);
			lastImage = image;
			OCImage image1 = image.copy();
			for (int iy = 0; iy < image.getHeightChars(); iy++) {
				for (int ix = 0; ix < image.getWidthChars(); ix++) {
					image1.set(ix, iy, image.getBG(ix, iy), image.getFG(ix, iy), image.getQuadrant(ix, iy) ^ 0xFF);
				}
			}

			OCImageDelta delta = new OCImageDelta(image.getWidthChars(), image.getHeightChars());
			delta.recalc(image1, image);

			List<OCCommandWeighted> weighteds = new ArrayList<>();
			iterate(image1, image, delta, weighteds, false, false);
			OCCommandContext context = new OCCommandContext();

			weighteds.sort((a, b) -> Double.compare(b.getWeightedValue(context, image1, image, delta, frameNumber), a.getWeightedValue(context, image1, image, delta, frameNumber)));
			for (OCCommandWeighted w : weighteds) {
				for (IOCCommand cmd : w.commands) {
					cmd.applyDistance(context, image1, image1, new OCImageDelta(image.getWidthChars(), image.getHeightChars()));
				}

				commands.addAll(w.commands);
			}

			commands.add(new OCCommandEndFrame());
			lastContext = context;
			lastUneditedImage = image;
			return;
		}

		OCImage nextImage = lastImage.copy();

		List<OCCommandWeighted> weightedSets = new ArrayList<>(8192);

		final OCImageDelta delta = new OCImageDelta(image.getWidthChars(), image.getHeightChars());
		delta.recalc(lastImage, image);

		iterate(lastImage, image, delta, weightedSets, false, true);
		iterate(lastImage, image, delta, weightedSets, true, true);
		int rectsCount = weightedSets.size();

		ColorRectangleFinderOld.getRectangles(image, lastImage).forEach((a) -> weightedSets.add(a.toWCommand()));
		ColorRectangleFinder.getRectangles(image, lastImage).forEach((a) -> weightedSets.add(a.toWCommand()));
		int fillsCount = weightedSets.size() - rectsCount;

		System.out.println("Have " + fillsCount + " fills, " + rectsCount + " sets.");

		OCCommandContext context = lastContext.copy(false);
		int cost = 0;
		int maxCost = 254;
		int cmds = 0;

		/* for (int iy = 0; iy < image.getHeightChars(); iy++) {
			for (int ix = 0; ix < image.getWidthChars(); ix++) {
				nextImage.set(ix, iy, 0, 0, 0);
			}
		} */

		/* for (OCCommandWeighted w : weightedSets) {
			IOCCommand ww = w.getLastCommand();
			if (ww instanceof OCCommandSetWithColor) {
				System.out.println(((OCCommandSetWithColor) ww).getX() + " " + ((OCCommandSetWithColor) ww).getY() + " " + ((OCCommandSetWithColor) ww).getQuadrantArray().length);
			}
		} */

		int i = 0;
		int lastSqueezePos = 0;

		List<IOCCommand> frameCommands = new ArrayList<>();

		boolean lastBreath = false;

		while (cost <= maxCost) {
			final OCCommandContext currContext = context;
			final boolean currentLB = lastBreath;
			final int currCost = cost;
			final OCImage currImage = nextImage;
			OCCommandWeighted w1 = weightedSets.parallelStream().filter((a) -> {
				if (a.used) return false;
				return !currentLB || a.getTotalCommandCost(currContext) <= (maxCost - currCost);
			}).min((a, b) -> {
				int divA = a.getTotalCommandCost(currContext);
				int divB = b.getTotalCommandCost(currContext);

				divA *= divA;
				divB *= divB;

				double vA = a.getWeightedValue(currContext, currImage, image, delta, frameNumber) / (divA);
				double vB = b.getWeightedValue(currContext, currImage, image, delta, frameNumber) / (divB);

				return Double.compare(vB, vA);
			}).orElse(null);

			double vCurr = 0;
			OCCommandWeighted w = null;

			if (w1 != null) {
				vCurr = w1.getWeightedValue(context, nextImage, image, delta, frameNumber);
				w = w1;
			}

			int thisCost = 0;
			if (w != null) {
				w.used = true;
				thisCost = w.getTotalCommandCost(context);
			}

			if (w == null || (cost + thisCost) >= maxCost) {
				// Try squeezing things up a bit
				boolean squeezed = false;

				IntSet changedPosUntil = new IntOpenHashSet();
				for (int ci = frameCommands.size() - 1; ci >= 0; ci--) {
					IOCCommand oci = frameCommands.get(ci);
					Optional<IntIterator> iti = oci.getChangedPositions(160, 160);
					if (iti.isPresent()) {
						IntSet changedPosMy = new IntOpenHashSet();
						while (iti.get().hasNext()) {
							changedPosMy.add(iti.get().nextInt());
						}

						boolean squeezedMe = false;
						//if (oci instanceof OCCommandSetWithColor) {
							if (changedPosUntil.containsAll(changedPosMy)) {
								squeezedMe = true;
								frameCommands.set(ci, null);
							}
						/* } else if (oci instanceof OCCommandFillWithColor) {
							int found = 0;
							for (int kf : changedPosMy) {
								if (changedPosUntil.contains(kf)) found++;
							}

							double fRatio = (double) found / changedPosMy.size();
							if (fRatio >= 0.7) {
								squeezedMe = true;
								frameCommands.set(ci, null);
							}
						} */

						if (!squeezedMe && (oci instanceof OCCommandFillWithColor) && (((OCCommandFillWithColor) oci).getWidth() >= 2 && ((OCCommandFillWithColor) oci).getHeight() >= 2)) {
							int oldCost = oci.getCost();

							Optional<OCCommandFillWithColor> fillCmd = ((OCCommandFillWithColor) oci).compress(changedPosUntil);
							if (fillCmd.isPresent()) {
								squeezed = true;
								int newCost = fillCmd.get().getCost();
								if (newCost < oldCost) {
									// System.out.println("reduced fill cost by " + (oldCost - newCost));
								}
								frameCommands.set(ci, fillCmd.get());
							}
						}

						if (frameCommands.get(ci) != oci) {
							if (frameCommands.get(ci) != null) {
								iti = frameCommands.get(ci).getChangedPositions(160, 160);
								while (iti.get().hasNext()) {
									changedPosUntil.add(iti.get().nextInt());
								}
							}
						} else {
							changedPosUntil.addAll(changedPosMy);
						}

						squeezed |= squeezedMe;
					}
				}

				if (squeezed) {
					OCImage testImage = lastImage.copy();
					OCCommandContext testContext = lastContext.copy(false);

					int newCost = 0;
					List<IOCCommand> newFrameCommands = new ArrayList<>();
					for (IOCCommand c : frameCommands) {
						if (c != null) {
							newFrameCommands.add(c);
							newCost += c.getCost(testContext);
							c.apply(testContext, testImage);
						}
					}

					// System.out.println("Squeezed " + frameCommands.size() + " (" + cost + ") -> " + newFrameCommands.size() + " (" + newCost + ")");
					frameCommands = newFrameCommands;
					cost = newCost;
					context = testContext;
					nextImage = testImage;
					delta.recalc(nextImage, image);
				}
			}

			if (w == null) {
				// All weighteds were used.
				break;
			}

			if (vCurr <= 1E-6) {
				if (lastBreath) break; else continue;
			}

			/* if (lastBreath) {
				System.out.println("found one more");
			} */

			if ((cost + thisCost) <= maxCost) {
				cost += thisCost;
				for (IOCCommand c : w.commands) {
					c.apply(context, nextImage);
					/* c.getChangedPositions(160, 160).ifPresent((it) -> {
						while (it.hasNext()) {
							lastChange[it.nextInt()] = frameNumber;
						}
					}); */
					delta.recalc(nextImage, image, c);
					cmds++;
				}
				frameCommands.addAll(w.commands);
			} else if (cost >= maxCost) {
				break;
			} else {
				// we can still cram s-something in, right?
				lastBreath = true;
			}

			/* if (((i++) & 15) == 0) {
				final OCCommandContext currContext2 = context;
				weightedSets.sort((a, b) -> Double.compare(b.getWeightedValue(currContext2, nextImage, image, frameNumber), a.getWeightedValue(currContext2, nextImage, image, frameNumber)));
			} */
		}

		frameCommands.add(new OCCommandEndFrame());
		commands.addAll(frameCommands);

		// recalc nextImage
		/* nextImage = lastImage.copy();
		OCCommandContext testContext = new OCCommandContext();
		for (IOCCommand c : commands) {
			c.apply(testContext, nextImage);
		} */

		double diff = ImageUtils.getDistance(
				lastImage, nextImage,
				0, 0,
				lastImage.getWidthChars(), lastImage.getHeightChars()
		) / 1000000.0;
		totalDiff += diff;

		System.out.println("Frame " + frameNumber + ": " + cmds + " commands, cost = " + cost + ", diff = " + diff + ", avg = " + (totalDiff / (imageList.size() + 1)));

		//imageList.add(nextImage);
		/* OCImage previewImage = nextImage.copy();
		for (IOCCommand cmd : frameCommands) {
			if (cmd instanceof OCCommandCopy) {
				((OCCommandCopy) cmd).mark(previewImage);
			}
		}
		imageList.add(previewImage); */
		imageList.add(nextImage);

		lastContext = context.copy(true);
		lastUneditedImage = image;
		lastImage = nextImage;
	}

	@Override
	public BufferedImage getPreprocessedFrame(IVideoReader reader, int image) {
		if (image == imageList.size() - 1 && image >= 0) {
			return lastUneditedImage.getPreview();
		} else {
			return OCUtils.from(reader.getFrame(image), OCUtils.getPaletteTier3(), ditherMatrix).getPreview();
		}
	}

	@Override
	public void write(OutputStream stream) throws IOException {
		int width = 0;
		int height = 0;

		if (imageList.size() > 0) {
			width = imageList.get(0).getWidthChars();
			height = imageList.get(0).getHeightChars();
		}

		stream.write(1);
		stream.write(width);
		stream.write(height);

		for (IOCCommand c : commands) {
			c.write(stream);
		}
	}

	@Override
	public BufferedImage getFrame(int frame) {
		return imageList.get(frame).getPreview();
	}
}
