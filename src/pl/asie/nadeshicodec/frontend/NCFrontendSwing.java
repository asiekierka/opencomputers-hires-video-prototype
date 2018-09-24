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

import pl.asie.nadeshicodec.codec.CodecManager;
import pl.asie.nadeshicodec.codec.CodecUtils;
import pl.asie.nadeshicodec.codec.VideoReaderFFMPEG;
import pl.asie.nadeshicodec.codec.nadeshiko.CodecNadeshiko;
import pl.asie.nadeshicodec.util.DitherMatrix;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

public class NCFrontendSwing {
	private final JFrame window;
	private final JPanel panel;
	private final JMenuBar menuBar;
	private final JMenu fileMenu;
	private final JMenuItem openItem, exportItem;
	private final JSlider frameSlider;
	private final JLabel frameNumberLabel;
	private final JLabel statusLabel;
	private final SimpleCanvas canvasInput, canvasOutput;
	private final CodecManager manager;
	private final JComboBox<String> ditherMethodBox;
	private final Map<String, DitherMatrix> ditherOptions;

	private final JButton playbackToggle;
	private boolean autoPlayback = false;

	public NCFrontendSwing(String windowName, CodecManager manager, Map<String, DitherMatrix> ditherOptions, String defaultDitherOption) {
		this.ditherOptions = ditherOptions;
		this.window = new JFrame(windowName);
		this.window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// add components
		canvasInput = new SimpleCanvas();
		canvasOutput = new SimpleCanvas();

		panel = new JPanel(new GridBagLayout());
		add(panel, new JLabel("Converted Input"), (c) -> { c.gridx = 0; c.gridy = 0; });
		add(panel, new JLabel("Encoded"), (c) -> { c.gridx = 1; c.gridy = 0; });
		add(panel, canvasInput, (c) -> { c.gridx = 0; c.gridy = 1; });
		add(panel, canvasOutput, (c) -> { c.gridx = 1; c.gridy = 1; });
		add(panel, frameSlider = new JSlider(), (c) -> { c.gridx = 0; c.gridy = 2; c.gridwidth = 2; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1; });
		add(panel, statusLabel = new JLabel("Ready."), (c) -> { c.gridx = 1; c.gridy = 3; c.anchor = GridBagConstraints.LINE_END; });

		JPanel optsPanel = new JPanel(new GridBagLayout());
		add(optsPanel, new JLabel("Dither method:"), (c) -> { c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.LINE_START; });

		String[] ditherKeys = ditherOptions.keySet().toArray(new String[0]);
		add(optsPanel, ditherMethodBox = new JComboBox<>(ditherKeys), (c) -> { c.gridx = 1; c.gridy = 0; c.anchor = GridBagConstraints.LINE_START; });
		for (int i = 0; i < ditherKeys.length; i++) {
			if (ditherKeys[i].equals(defaultDitherOption))
				ditherMethodBox.setSelectedIndex(i);
		}
		add(optsPanel, playbackToggle = new JButton("Toggle Playback"), (c) -> { c.gridx = 0; c.gridy = 1; c.anchor = GridBagConstraints.LINE_START; });

		add(panel, frameNumberLabel = new JLabel("..."), (c) -> { c.gridx = 0; c.gridy = 3; c.anchor = GridBagConstraints.LINE_START; });
		add(panel, optsPanel, (c) -> { c.gridx = 0; c.gridy = 4; c.anchor = GridBagConstraints.LINE_START; });

		playbackToggle.addActionListener((event) -> {
			autoPlayback = !autoPlayback;
		});

		frameSlider.setSnapToTicks(true);
		frameSlider.addChangeListener(this::onFrameSliderChanged);

		window.setJMenuBar(menuBar = new JMenuBar());
		menuBar.add(fileMenu = new JMenu("File"));
		fileMenu.add(openItem = new JMenuItem("Open"));
		fileMenu.add(exportItem = new JMenuItem("Render"));

		openItem.setAccelerator(KeyStroke.getKeyStroke(
				KeyEvent.VK_O, InputEvent.CTRL_MASK
		));
		exportItem.setAccelerator(KeyStroke.getKeyStroke(
				KeyEvent.VK_S, InputEvent.CTRL_MASK
		));

		openItem.addActionListener(this::onOpen);
		exportItem.addActionListener(this::onExport);

		Dimension canvDim = new Dimension(640, 400);
		canvasInput.setPreferredSize(canvDim);
		canvasOutput.setPreferredSize(canvDim);

		window.add(panel);
		window.pack();
		window.setVisible(true);

		this.manager = manager;
		setFrameCount(manager.getFrameCount());
	}

	public void onOpen(ActionEvent event) {
		JFileChooser fc = new JFileChooser();
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
		int returnVal = fc.showOpenDialog(window);

		if (returnVal == JFileChooser.APPROVE_OPTION) {
			loadFile(fc.getSelectedFile());
		}
	}

	public void onExport(ActionEvent event) {
		if (manager.getFrameCount() > 0) {
			JFileChooser fc = new JFileChooser();
			fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
			int returnVal = fc.showSaveDialog(window);

			if (returnVal != JFileChooser.APPROVE_OPTION) {
				return;
			}

			Runnable r = () -> {
				frameSlider.setEnabled(false);
				manager.getOutputFrame(manager.getFrameCount() - 1, frameSlider::setValue);
				frameSlider.setEnabled(true);

				statusLabel.setText("Saving...");
				statusLabel.repaint();

				// all frames rendered
				try {
					manager.write(fc.getSelectedFile());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}

				statusLabel.setText("Saved!");
				statusLabel.repaint();
			};
			Thread t = new Thread(r);
			t.start();
		}
	}

	public void loadFile(File f) {
		autoPlayback = false;

		boolean ignoreAspectRatio = false;

		Runnable r = () -> {
			try {
				frameSlider.setEnabled(false);
				//noinspection SuspiciousMethodCalls
				manager.setReaderCodec(
						new VideoReaderFFMPEG(f, 20, (a) -> {
							statusLabel.setText(a);
							statusLabel.repaint();
						}, CodecUtils::scaleDimensionsDefault),
						new CodecNadeshiko(ditherOptions.get(ditherMethodBox.getSelectedItem()))
				);
				setFrameCount(manager.getFrameCount());
				frameSlider.setEnabled(true);
			} catch (IOException e) {
				frameSlider.setEnabled(true);
				throw new RuntimeException(e);
			}
		};
		Thread t = new Thread(r);
		t.start();
	}

	public boolean isAutoPlayback() {
		return autoPlayback;
	}

	public int getFrameCount() {
		return manager.getFrameCount();
	}

	public int getFrame() {
		return frameSlider.getValue() - 1;
	}

	public void setFrame(int i) {
		frameSlider.setValue(i + 1);
	}

	public void setFrameCount(int count) {
		if (count <= 0) {
			frameSlider.setMinimum(0);
			frameSlider.setValue(0);
			frameSlider.setMaximum(0);
		} else {
			frameSlider.setMinimum(1);
			frameSlider.setValue(1);
			frameSlider.setMaximum(count);
		}
		this.onFrameSliderChanged(null);
	}

	protected void onFrameSliderChanged(ChangeEvent event) {
		if (frameSlider.getValue() >= 1 && manager.getFrameCount() >= 1) {
			BufferedImage oldImage = canvasInput.getImage();
			BufferedImage newImage = manager.getInputFrame(frameSlider.getValue() - 1);
			Dimension canvDim = new Dimension(newImage.getWidth() * 2, newImage.getHeight() * 2);

			if (!canvDim.equals(canvasInput.getPreferredSize())) {
				canvasInput.setPreferredSize(canvDim);
				canvasOutput.setPreferredSize(canvDim);
				window.pack();
			}

			canvasInput.setImage(newImage);
			canvasOutput.setImage(manager.getOutputFrame(frameSlider.getValue() - 1, (v) -> {
				statusLabel.setText("Rendering frame " + v);
				statusLabel.repaint();
			}));
		}
		frameNumberLabel.setText("Frame " + frameSlider.getValue() + "/" + frameSlider.getMaximum());
		statusLabel.setText("Ready.");
		statusLabel.repaint();
	}

	private void add(JPanel panel, Component c, Consumer<GridBagConstraints> cc) {
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(4, 4, 4, 4);
		cc.accept(gbc);
		panel.add(c, gbc);
	}
}
