package com.teamten.image;

import org.junit.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Unit test for the resize operation.
 */
public class ResizeOpTest {
    /**
     * Make sure we handle alpha properly.
     */
    @Test
    public void resizeWithAlpha() throws IOException {
        BufferedImage input = ImageUtils.makeTransparent(1024, 1024);

        Graphics2D g = ImageUtils.createGraphics(input);
        g.setPaint(Color.WHITE);
        g.fillArc(256, 256, 512, 512, 0, 360);
        g.dispose();

        ImageUtils.save(input, "input_alpha.png");

        BufferedImage output = ImageUtils.resizeToFit(input, 72, 72);
        ImageUtils.save(output, "output_alpha.png");
    }

    /**
     * Make sure we handle gamma properly in resize.
     */
    @Test
    public void resizeWithGamma() throws IOException {
        BufferedImage input = ImageUtils.makeCheckerboard(1024, 1024, Color.BLACK, Color.WHITE, 1);
        ImageUtils.save(input, "input_resize_gamma.png");

        BufferedImage output = ImageUtils.resizeToFit(input, 72, 72);
        ImageUtils.save(output, "output_resize_gamma.png");
    }

    /**
     * Make sure we handle gamma properly in blur.
     */
    @Test
    public void blurWithGamma() throws IOException {
        BufferedImage input = ImageUtils.makeCheckerboard(1024, 1024, Color.BLACK, Color.WHITE, 1);
        ImageUtils.save(input, "input_blur_gamma.png");

        BufferedImage output = ImageUtils.blur(input, 5.0);
        ImageUtils.save(output, "output_blur_gamma.png");
    }
}