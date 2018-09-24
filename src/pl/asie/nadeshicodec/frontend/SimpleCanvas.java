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

package pl.asie.nadeshicodec.frontend;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class SimpleCanvas extends JComponent {
	private BufferedImage image;

	@Override
	public void paintComponent(Graphics graphics) {
		if (image != null) {
			Dimension size = getSize();
			if (image.getWidth() == size.width && image.getHeight() == size.height) {
				graphics.drawImage(image, 0, 0, null);
			} else {
				Graphics2D g2d = (Graphics2D) graphics.create();
				g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
				g2d.drawImage(image, 0, 0, size.width, size.height, 0, 0, image.getWidth(), image.getHeight(), null);
				g2d.dispose();
			}
		}
	}

	public void setImage(BufferedImage image) {
		this.image = image;
		repaint();
	}

	public BufferedImage getImage() {
		return this.image;
	}
}
