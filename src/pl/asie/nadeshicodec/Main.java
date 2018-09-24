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

package pl.asie.nadeshicodec;

import com.google.common.collect.ImmutableMap;
import pl.asie.nadeshicodec.codec.CodecManager;
import pl.asie.nadeshicodec.codec.CodecUtils;
import pl.asie.nadeshicodec.codec.VideoReaderFFMPEG;
import pl.asie.nadeshicodec.codec.nadeshiko.CodecNadeshiko;
import pl.asie.nadeshicodec.frontend.NCFrontendSwing;
import pl.asie.nadeshicodec.util.DitherMatrix;
import pl.asie.nadeshicodec.util.colorspace.Colorspaces;

import java.io.File;

public class Main {
    public static void main(String[] args) throws Exception {
        Colorspaces.init();

        if (args.length >= 2) {
            File in = new File(args[0]);
            File out = new File(args[1]);

            CodecManager manager = new CodecManager();
            manager.setReaderCodec(
                    new VideoReaderFFMPEG(in, 20, System.out::println, CodecUtils::scaleDimensionsDefault),
                    new CodecNadeshiko(new DitherMatrix(new int[]{0,2,3,1}))
            );
            manager.getOutputFrame(manager.getFrameCount() - 1, (a) -> {
                System.out.println("Rendering frame " + a);
            });
            manager.write(out);
            return;
        }

        NCFrontendSwing frontend = new NCFrontendSwing("Nadeshicodec",
                new CodecManager(),
                ImmutableMap.of(
                        "none", new DitherMatrix(),
                        "2x2", new DitherMatrix(new int[]{0,2,3,1})
                ), "2x2"
        );

        if (args.length > 0) {
            File f = new File(args[0]);
            if (f.exists()) {
                frontend.loadFile(f);
            }
        }

        while (true) {
            long time = System.currentTimeMillis();

            if (frontend.isAutoPlayback() && frontend.getFrame() < (frontend.getFrameCount() - 1)) {
                frontend.setFrame(frontend.getFrame() + 1);
            }

            time = System.currentTimeMillis() - time;

            if (time < 50) {
                Thread.sleep(50 - time);
            }
        }
    }
}
