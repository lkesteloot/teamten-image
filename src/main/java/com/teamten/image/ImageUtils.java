/*
 *
 *    Copyright 2016 Lawrence Kesteloot
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

// Copyright 2011 Lawrence Kesteloot

package com.teamten.image;

import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.text.AttributedCharacterIterator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Assorted utility methods for transforming images.
 */
public class ImageUtils {
    private static final File MY_FONT_DIR = new File("/Users/lk/Dropbox/Personal/Fonts");
    private static final String FONT_DIR = System.getProperty("user.home") +
        File.separator + "fonts";
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    private static PrintStream mLogger = System.out;

    /**
     * Create a new image of the given size with all pixels transparent.
     *
     * @param width width of the new image in pixels.
     * @param height height of the new image in pixels.
     * @return a transparent 4-byte ABGR image of the specified size.
     */
    public static BufferedImage makeTransparent(int width, int height) {
        log("Making a transparent image (%dx%d)", width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g = createGraphics(image);
        g.setBackground(TRANSPARENT);
        g.clearRect(0, 0, width, height);
        g.dispose();

        return image;
    }

    /**
     * Create a new image of the given size with a given background color.
     *
     * @param width width of the new image in pixels.
     * @param height height of the new image in pixels.
     * @param color color to fill the image with.
     * @return a 3-byte BGR or 4-byte ABGR image (spending on the translucency
     * of the color) of the specified size, filled with the specified color.
     */
    public static BufferedImage make(int width, int height, Color color) {
        int type;
        if (color.getTransparency() != Transparency.OPAQUE) {
            type = BufferedImage.TYPE_4BYTE_ABGR;
        } else {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }

        log("Making an image of color %s, type %s (%dx%d)", color, getTypeName(type), width, height);

        BufferedImage image = new BufferedImage(width, height, type);

        Graphics2D g = createGraphics(image);
        g.setBackground(color);
        g.clearRect(0, 0, width, height);
        g.dispose();

        return image;
    }

    /**
     * Create a new image of the given size with a white background.
     *
     * @param width width of the new image in pixels.
     * @param height height of the new image in pixels.
     * @return a 3-byte BGR white image of the specified size.
     */
    public static BufferedImage makeWhite(int width, int height) {
        return make(width, height, Color.WHITE);
    }

    /**
     * Creates an image of the specified size with a linear gradient going from begin
     * to end, interpolating the specified colors linearly. Pixels
     * past the end of the line are the color of that end.
     *
     * @param width width of the new image in pixels.
     * @param height height of the new image in pixels.
     * @param beginX X coordinate of the starting gradient location.
     * @param beginY Y coordinate of the starting gradient location.
     * @param beginColor color at the starting location. Must be opaque.
     * @param endX X coordinate of the ending gradient location.
     * @param endY Y coordinate of the ending gradient location.
     * @param endColor color at the ending location. Must be opaque.
     * @return a 3-byte BGR image of the specified size with the specified gradient.
     */
    public static BufferedImage makeLinearGradient(int width, int height,
            int beginX, int beginY, Color beginColor, int endX, int endY, Color endColor) {

        log("Making a linear gradient image (%dx%d)", width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        double length = getDistance(beginX, beginY, endX, endY);
        double vx = (endX - beginX) / length;
        double vy = (endY - beginY) / length;

        int beginR = beginColor.getRed();
        int beginG = beginColor.getGreen();
        int beginB = beginColor.getBlue();
        int endR = endColor.getRed();
        int endG = endColor.getGreen();
        int endB = endColor.getBlue();
        int diffR = endR - beginR;
        int diffG = endG - beginG;
        int diffB = endB - beginB;

        for (int y = 0; y < height; y++) {
            int dy = y - beginY;

            for (int x = 0; x < width; x++) {
                int dx = x - beginX;
                double dot = (dx*vx + dy*vy) / length;

                // In per-mil of the radius.
                int dist = (int) (Math.min(Math.max(dot, 0.0), 1.0) * 1000.0);

                int r = beginR + dist*diffR/1000;
                int g = beginG + dist*diffG/1000;
                int b = beginB + dist*diffB/1000;

                int rgb = (r << 16) | (g << 8) | b;

                image.setRGB(x, y, rgb);
            }
        }

        return image;
    }

    /**
     * Creates an image of the specified size with a circular gradient going from center
     * to the edge (of the circle), interpolating the specified colors linearly. Pixels
     * past the edge of the circle are the color of the edge.
     *
     * @param width width of the new image in pixels.
     * @param height height of the new image in pixels.
     * @param centerX X coordinate of the center of the gradient.
     * @param centerY Y coordinate of the center of the gradient.
     * @param centerColor color at the center. Must be opaque.
     * @param edgeX X coordinate of the edge gradient location.
     * @param edgeY Y coordinate of the edge gradient location.
     * @param edgeColor color at the edge location. Must be opaque.
     * @return a 3-byte BGR image of the specified size with the specified gradient.
     */
    public static BufferedImage makeCircularGradient(int width, int height,
            int centerX, int centerY, Color centerColor, int edgeX, int edgeY, Color edgeColor) {

        log("Making a circular gradient image (%dx%d)", width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);

        double radius = getDistance(centerX, centerY, edgeX, edgeY);

        int centerR = centerColor.getRed();
        int centerG = centerColor.getGreen();
        int centerB = centerColor.getBlue();
        int edgeR = edgeColor.getRed();
        int edgeG = edgeColor.getGreen();
        int edgeB = edgeColor.getBlue();
        int diffR = edgeR - centerR;
        int diffG = edgeG - centerG;
        int diffB = edgeB - centerB;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // In per-mil of the radius.
                int dist = (int) (Math.min(getDistance(x, y, centerX, centerY)/radius, 1.0)
                        * 1000.0);

                int r = centerR + dist*diffR/1000;
                int g = centerG + dist*diffG/1000;
                int b = centerB + dist*diffB/1000;

                int rgb = (r << 16) | (g << 8) | b;

                image.setRGB(x, y, rgb);
            }
        }

        return image;
    }

    /**
     * Returns the distance between two pixels.
     */
    private static double getDistance(int x1, int y1, int x2, int y2) {
        int dx = x1 - x2;
        int dy = y1 - y2;

        return Math.sqrt(dx*dx + dy*dy);
    }

    /**
     * Creates a high-quality graphics objects for this object.
     *
     * @param image image to crate graphics context for.
     * @return a graphics context with high quality settings.
     */
    public static Graphics2D createGraphics(BufferedImage image) {
        Graphics2D g = image.createGraphics();

        g.setRenderingHints(getHighQualityRenderingMap());

        return g;
    }

    /**
     * Return the image with a new type.
     *
     * @param src image to convert. It is not modified.
     * @param newType the new image type. See BufferedImage.TYPE_...
     * @return a copy of the source image, but with the new type.
     */
    public static BufferedImage convertType(BufferedImage src, int newType) {
        // Annoying log.
        /// log("Converting image to type %d (%dx%d)", newType, src.getWidth(), src.getHeight());

        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), newType);
        pasteInto(dest, src, 0, 0);
        return dest;
    }

    /**
     * Returns the name of the image type.
     *
     * @param imageType an image type. See BufferedImage.TYPE_...
     * @return a string representing the image type. For example, BufferedImage.TYPE_3BYTE_BGR
     * will return "3BYTE_BGR".
     */
    public static String getTypeName(int imageType) {
        switch (imageType) {
            case BufferedImage.TYPE_INT_RGB: return "INT_RGB";
            case BufferedImage.TYPE_INT_ARGB: return "INT_ARGB";
            case BufferedImage.TYPE_INT_ARGB_PRE: return "INT_ARGB_PRE";
            case BufferedImage.TYPE_INT_BGR: return "INT_BGR";
            case BufferedImage.TYPE_3BYTE_BGR: return "3BYTE_BGR";
            case BufferedImage.TYPE_4BYTE_ABGR: return "4BYTE_ABGR";
            case BufferedImage.TYPE_4BYTE_ABGR_PRE: return "4BYTE_ABGR_PRE";
            case BufferedImage.TYPE_BYTE_GRAY: return "BYTE_GRAY";
            case BufferedImage.TYPE_BYTE_BINARY: return "BYTE_BINARY";
            case BufferedImage.TYPE_BYTE_INDEXED: return "BYTE_INDEXED";
            case BufferedImage.TYPE_USHORT_GRAY: return "USHORT_GRAY";
            case BufferedImage.TYPE_USHORT_565_RGB: return "USHORT_565_RGB";
            case BufferedImage.TYPE_USHORT_555_RGB: return "USHORT_555_RGB";
            case BufferedImage.TYPE_CUSTOM: return "CUSTOM";
            default: return "Unknown image type";
        }
    }

    /**
     * Return the number of bytes per pixel for this image.
     *
     * @param image image to analyze.
     * @return either 3 or 4, depending on the type of the image.
     * @throws IllegalArgumentException if the type is not TYPE_3BYTE_BGR or
     * TYPE_4BYTE_ABGR. Do not expand this set without checking all callers, since
     * they may be depending on this restricted set.
     */
    public static int getBytesPerPixel(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_3BYTE_BGR) {
            return 3;
        } else if (image.getType() == BufferedImage.TYPE_4BYTE_ABGR) {
            return 4;
        } else {
            throw new IllegalArgumentException("Image type must be 3BYTE_BGR or 4BYTE_ABGR, not " +
                    getTypeName(image.getType()));
        }
    }

    /**
     * Make a copy of the image.
     *
     * @param image image to copy.
     * @return a copy of the image, with the same pixels and type.
     */
    public static BufferedImage copy(BufferedImage image) {
        return convertType(image, image.getType());
    }

    /**
     * Compose images over one another. The first layer in the array is the lowest one.
     *
     * @param layers array of layers, from lowest (obscured) to highest (visible).
     * @return composed 4-byte ABGR image.
     */
    public static BufferedImage compose(BufferedImage ... layers) {
        int width = 0;
        int height = 0;

        for (BufferedImage layer : layers) {
            width = Math.max(width, layer.getWidth());
            height = Math.max(height, layer.getHeight());
        }

        log("Composing %d images (%dx%d)", layers.length, width, height);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);

        Graphics2D g = createGraphics(image);
        for (BufferedImage layer : layers) {
            g.drawImage(layer, 0, 0, null);
        }
        g.dispose();

        return image;
    }

    /**
     * Returns the shadow of the input image. The shadow is based only on the
     * alpha channel.
     *
     * @param image source image. Must be semi-transparent, since its alpha channel
     * is used to make the shadow.
     * @param radius the size of the shadow, in pixels.
     * @param darkness how dark to make the shadow, where 0.0 means
     * none and 1.0 is the darkest.
     * @return an image of the same size as the source with just the shadow of it.
     */
    public static BufferedImage makeShadow(BufferedImage image, double radius, double darkness) {
        log("Making a shadow of radius %g and darkness %g", radius, darkness);

        BufferedImage shadow = new BufferedImage(image.getWidth(), image.getHeight(),
                BufferedImage.TYPE_4BYTE_ABGR);

        // Make an opaque image where gray = alpha of original.
        int width = shadow.getWidth();
        int height = shadow.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                int alpha = argb >>> 24;

                argb = 0xFF000000 | (alpha << 16) | (alpha << 8) | (alpha << 0);

                shadow.setRGB(x, y, argb);
            }
        }

        // Blur that.
        shadow = blur(shadow, radius);

        // Make a semi-transparent image where the color is black and the alpha is based
        // on the blurred color above.
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = shadow.getRGB(x, y);

                int alpha = argb & 0xFF;

                // Darken or lighten shadow.
                alpha = (int) (alpha * darkness);
                alpha = Math.min(Math.max(alpha, 0), 255);

                // Not premultiplied, so we can just set color to black.
                argb = alpha << 24;

                shadow.setRGB(x, y, argb);
            }
        }

        return shadow;
    }

    /**
     * Return an image with the original and its reflection.
     *
     * @param image original image.
     * @param reflectionHeightFraction the fraction of the original height to make visible
     * in the reflection. 0.2 is a good value here.
     * @return a new image, taller than the original, with the source image on
     * top and the bottom part of the source image reflected at the bottom.
     */
    public static BufferedImage addReflection(BufferedImage image,
            double reflectionHeightFraction) {

        log("Adding a reflection");

        // Input image size.
        int width = image.getWidth();
        int height = image.getHeight();

        // Add reflection.
        int reflectionHeight = (int) (height * reflectionHeightFraction);
        int fullHeight = height + reflectionHeight;

        // Compose the full image.
        BufferedImage fullImage = new BufferedImage(width, fullHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = createGraphics(fullImage);

        // Draw original.
        g.drawImage(image, 0, 0, null);

        // Draw reflection.
        g.drawImage(flipVertically(image), 0, height, null);

        // Fade out the reflection.
        int luminanceTop = 128;
        int luminanceBottom = 255;
        double luminanceExp = 0.5;

        for (int y = 0; y < reflectionHeight; y++) {
            double t = (float) y / (reflectionHeight - 1);

            t = Math.pow(t, luminanceExp);

            int luminance = luminanceTop + (int) (t*(luminanceBottom - luminanceTop));

            g.setColor(new Color(255, 255, 255, luminance));
            g.drawLine(0, height + y, width - 1, height + y);
        }
        g.dispose();

        return fullImage;
    }

    /**
     * Blurs an image using a high-quality high-speed two-pass algorithm.
     * Good stuff about blurring: http://www.jhlabs.com/ip/blurring.html
     *
     * @param image source image.
     * @param radius blur radius.
     * @return a blurred version of the source image.
     */
    public static BufferedImage blur(BufferedImage image, double radius) {
        log("Blurring with radius %g", radius);

        DecomposableConvolveOp op = new DecomposableConvolveOp(
                DecomposableConvolveOp.makeGaussianKernel(radius), 1);
        return op.filter(image, null);
    }

    /**
     * Brightens an image and blurs it, clipping to white.
     * @param image source image.
     * @param brightness factor to brighten. 1.0 makes this function behave like blur().
     * @param radius blur radius.
     * @return a brightened and blurred version of the source image.
     */
    public static BufferedImage glow(BufferedImage image, double brightness, double radius) {
        log("Glowing with brightness %g and radius %g", brightness, radius);

        DecomposableConvolveOp op = new DecomposableConvolveOp(
                DecomposableConvolveOp.makeGaussianKernel(radius), brightness);
        return op.filter(image, null);
    }

    /**
     * Returns a copy of the image, rotated clockwise by "radians" radians.
     *
     * @param image source image.
     * @param radians how much to rotate.
     * @param fitNewImage to make the new image fit the rotate image. Otherwise
     * the new image is the same size and position as the input image.
     * @return a rotated version of the source image.
     */
    public static BufferedImage rotate(BufferedImage image, double radians, boolean fitNewImage) {
        log("Rotating %d degrees clockwise", (int) Math.round(radians*180/Math.PI));

        AffineTransform transform = AffineTransform.getRotateInstance(radians,
                image.getWidth()/2.0, image.getHeight()/2.0);

        AffineTransformOp op = new AffineTransformOp(transform, getHighQualityRenderingHints());

        int cropWidth;
        int cropHeight;
        if (fitNewImage) {
            // Make sure new image fits bounds of rotated image.
            Rectangle2D bounds = op.getBounds2D(image);
            if (bounds.getMinX() != 0 || bounds.getMinY() != 0) {
                // We need to move the transform so that negative pixels don't get clipped.
                AffineTransform translate = AffineTransform.getTranslateInstance(
                        -bounds.getMinX(), -bounds.getMinY());
                translate.concatenate(transform);
                transform = translate;
                op = new AffineTransformOp(transform, getHighQualityRenderingHints());
            }
            cropWidth = (int) Math.ceil(bounds.getWidth());
            cropHeight = (int) Math.ceil(bounds.getHeight());
        } else {
            cropWidth = image.getWidth();
            cropHeight = image.getHeight();
        }

        return crop(convertType(op.filter(image, null), image.getType()),
                0, 0, cropWidth, cropHeight);
    }

    /**
     * Return the image rotated counter-clockwise 90 degrees. The new image fits the rotated orientation.
     */
    public static BufferedImage rotateLeft(BufferedImage sourceImage) {
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();

        BufferedImage destImage = new BufferedImage(height, width, sourceImage.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destImage.setRGB(y, width - x - 1, sourceImage.getRGB(x, y));
            }
        }

        return destImage;
    }

    /**
     * Return the image rotated clockwise 90 degrees. The new image fits the rotated orientation.
     */
    public static BufferedImage rotateRight(BufferedImage sourceImage) {
        int width = sourceImage.getWidth();
        int height = sourceImage.getHeight();

        BufferedImage destImage = new BufferedImage(height, width, sourceImage.getType());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                destImage.setRGB(height - y - 1, x, sourceImage.getRGB(x, y));
            }
        }

        return destImage;
    }

    /**
     * Returns a copy of the image, flipped vertically.
     *
     * @param image source image.
     * @return a copy of the source image, flipped vertically (top to bottom).
     */
    public static BufferedImage flipVertically(BufferedImage image) {
        log("Flipping vertically");

        AffineTransformOp op = new AffineTransformOp(
                new AffineTransform(1.0, 0.0, 0.0, -1.0, 0.0, image.getHeight()),
                getHighQualityRenderingHints());
        return op.filter(image, null);
    }

    /**
     * Returns a copy of the image, flipped horizontally.
     *
     * @param image source image.
     * @return a copy of the source image, flipped horizontally (left to right).
     */
    public static BufferedImage flipHorizontally(BufferedImage image) {
        log("Flipping horizontally");

        AffineTransformOp op = new AffineTransformOp(
                new AffineTransform(-1.0, 0.0, 0.0, 1.0, image.getWidth(), 0.0),
                getHighQualityRenderingHints());
        return op.filter(image, null);
    }

    /**
     * Adds a margin to the image. The margin will be of the specified color,
     * unless the color, unless the color is null, in which case the margin
     * will be transparent.
     *
     * @param src source image.
     * @param top number of pixels to add on top.
     * @param right number of pixels to add on the right.
     * @param bottom number of pixels to add on the bottom.
     * @param left number of pixels to add on the left.
     * @param color color for the margin, or transparent if null.
     * @return a copy of the source image with the specified border.
     */
    public static BufferedImage addMargin(BufferedImage src,
            int top, int right, int bottom, int left, Color color) {

        log("Adding a margin (%d,%d,%d,%d)", top, right, bottom, left);

        int width = src.getWidth() + left + right;
        int height = src.getHeight() + top + bottom;
        BufferedImage dest;

        if (color == null) {
            dest = makeTransparent(width, height);
        } else {
            dest = make(width, height, color);
        }

        pasteInto(dest, src, left, top);

        return dest;
    }

    /**
     * Returns a resized image.
     *
     * @param image source image.
     * @param width new image width in pixels.
     * @param height new image height in pixels.
     * @return a resized copy of the source image.
     */
    public static BufferedImage resize(BufferedImage image, int width, int height) {
        log("Resizing from (%dx%d) to (%dx%d)",
                image.getWidth(), image.getHeight(), width, height);

        ResizeOp op = new ResizeOp(width, height);
        return op.filter(image, null);
    }

    /**
     * Returns an image scaled by a particular ratio, where 0.5 means half width and
     * half height.
     *
     * @param image source image.
     * @param ratio ratio of new image size to old image size.
     * @return a resized copy of the source image.
     */
    public static BufferedImage scale(BufferedImage image, double ratio) {
        return resize(image,
                (int) (image.getWidth()*ratio + 0.5),
                (int) (image.getHeight()*ratio + 0.5));
    }

    /**
     * Returns the image resized to fit in this size but keep the original aspect
     * ratio. Use 0 as either size (but not both) to mean "infinity".
     *
     * @param image source image.
     * @param width max width of new image. Use 0 for "infinity".
     * @param height max height of new image. Use 0 for "infinity".
     * @return a copy of the source image resized to fit in the specified rectangle,
     * keeping the original aspect ratio.
     */
    public static BufferedImage resizeToFit(BufferedImage image, int width, int height) {
        int fitWidth = width;
        int fitHeight = height;

        if (width == 0 && height == 0) {
            throw new IllegalArgumentException("Must specify either width or height");
        }

        if (width != 0 && height != 0) {
            if (image.getWidth() * height < image.getHeight() * width) {
                width = 0;
            } else {
                height = 0;
            }
        }

        if (width == 0) {
            width = image.getWidth() * height / image.getHeight();
        } else {
            height = image.getHeight() * width / image.getWidth();
        }

        log("Resizing from (%dx%d) to fit (%dx%d), final size is (%dx%d)",
                image.getWidth(), image.getHeight(), fitWidth, fitHeight, width, height);

        return resize(image, width, height);
    }

    /**
     * Like resizeToFit() but only shrinks. If the image is smaller, it is returned.
     * Returns the image shrunk to fit in this size but keep the original aspect
     * ratio. Use 0 as either size (but not both) to mean "infinity".
     *
     * @param image source image.
     * @param width max width of new image. Use 0 for "infinity".
     * @param height max height of new image. Use 0 for "infinity".
     * @return a copy of the source image resized to fit in the specified rectangle,
     * keeping the original aspect ratio. If the resized image would be larger,
     * the original image is returned.
     */
    public static BufferedImage shrinkToFit(BufferedImage image, int width, int height) {
        int fitWidth = width;
        int fitHeight = height;

        if (width == 0 && height == 0) {
            throw new IllegalArgumentException("Must specify either width or height");
        }

        if (width != 0 && height != 0) {
            if (image.getWidth() * height < image.getHeight() * width) {
                width = 0;
            } else {
                height = 0;
            }
        }

        if (width == 0) {
            width = image.getWidth() * height / image.getHeight();
        } else {
            height = image.getHeight() * width / image.getWidth();
        }

        // See if the computed size is smaller.
        if (width < image.getWidth() && height < image.getHeight()) {
            log("Shrinking from (%dx%d) to fit (%dx%d), final size is (%dx%d)",
                    image.getWidth(), image.getHeight(), fitWidth, fitHeight, width, height);

            image = resize(image, width, height);
        }

        return image;
    }

    /**
     * Finds the trimming rectangle.
     *
     * @param image source image.
     * @return the rectangle representing the part of the image that's not like the
     * frame (specifically, not like the upper-left corner).
     * @throws IllegalArgumentException if the entire image is the same color.
     */
    public static Rectangle getTrimmingRectangle(BufferedImage image) {
        // Inclusive rectangle.
        int x1 = 0;
        int y1 = 0;
        int x2 = image.getWidth() - 1;
        int y2 = image.getHeight() - 1;

        // Color we're removing from upper-left corner.
        int trimColor = image.getRGB(0, 0);

        // Move top down.
        while (y1 <= y2 && rowHasColor(image, y1, trimColor)) {
            y1++;
        }

        // Move bottom up.
        while (y2 >= y1 && rowHasColor(image, y2, trimColor)) {
            y2--;
        }

        // Move left right.
        while (x1 <= x2 && columnHasColor(image, x1, trimColor)) {
            x1++;
        }

        // Move right left.
        while (x2 >= x1 && columnHasColor(image, x2, trimColor)) {
            x2--;
        }

        if (y1 > y2 || x1 > x2) {
            throw new IllegalArgumentException("Entire image is the same color");
        }

        return Rectangle.makeFromInclusive(x1, y1, x2, y2);
    }

    /**
     * Removes any constant color around an image. The color is determined by the upper-left
     * pixel. Returns the sub-image, which shares the raster data with the
     * input image.
     *
     * @param image source image.
     * @return a copy of the image with any constant-color border removed.
     * @throws IllegalArgumentException if the entire image is a single color.
     */
    public static BufferedImage trim(BufferedImage image) {
        // Figure out what we're trimming.
        Rectangle rectangle = getTrimmingRectangle(image);

        // Bleah.
        int trimColor = image.getRGB(0, 0);

        log("Trimming (%dx%d) down to %s based on color %s",
                image.getWidth(), image.getHeight(),
                rectangle, new Color(trimColor, true));

        // We copy the subimage because a reference to it causes problems later
        // when scaling. We could later try to get the stride right in StretchOp,
        // though I couldn't immediately figure out how to do that.
        return copy(image.getSubimage(rectangle.getX(), rectangle.getY(),
                    rectangle.getWidth(), rectangle.getHeight()));
    }

    /**
     * Returns true if an entire row has the specified color.
     */
    private static boolean rowHasColor(BufferedImage image, int y, int color) {
        int width = image.getWidth();

        for (int x = 0; x < width; x++) {
            if (image.getRGB(x, y) != color) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns true if an entire column has the specified color.
     */
    private static boolean columnHasColor(BufferedImage image, int x, int color) {
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            if (image.getRGB(x, y) != color) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the left part of an image.
     *
     * @param src source image.
     * @param pixels the number of pixels to keep from the left side.
     * @return an image that's a copy of the left side of the source image.
     */
    public static BufferedImage left(BufferedImage src, int pixels) {
        BufferedImage dest = new BufferedImage(pixels, src.getHeight(), src.getType());
        pasteInto(dest, src, 0, 0);
        return dest;
    }

    /**
     * Returns the right part of an image.
     *
     * @param src source image.
     * @param pixels the number of pixels to keep from the right side.
     * @return an image that's a copy of the right side of the source image.
     */
    public static BufferedImage right(BufferedImage src, int pixels) {
        BufferedImage dest = new BufferedImage(pixels, src.getHeight(), src.getType());
        pasteInto(dest, src, pixels - src.getWidth(), 0);
        return dest;
    }

    /**
     * Returns the top part of an image.
     *
     * @param src source image.
     * @param pixels the number of pixels to keep from the top side.
     * @return an image that's a copy of the top side of the source image.
     */
    public static BufferedImage top(BufferedImage src, int pixels) {
        BufferedImage dest = new BufferedImage(src.getWidth(), pixels, src.getType());
        pasteInto(dest, src, 0, 0);
        return dest;
    }

    /**
     * Returns the bottom part of an image.
     *
     * @param src source image.
     * @param pixels the number of pixels to keep from the bottom side.
     * @return an image that's a copy of the bottom side of the source image.
     */
    public static BufferedImage bottom(BufferedImage src, int pixels) {
        BufferedImage dest = new BufferedImage(src.getWidth(), pixels, src.getType());
        pasteInto(dest, src, 0, pixels - src.getHeight());
        return dest;
    }

    /**
     * Returns the part of the image with the upper-left coordinate at x, y and
     * of width and height.
     *
     * @param src source image.
     * @param x x coordinate of top left of the cropping rectangle.
     * @param y y coordinate of top left of the cropping rectangle.
     * @param width width of the cropping rectangle.
     * @param height height of the cropping rectangle.
     * @return a cropped copy of the source image.
     */
    public static BufferedImage crop(BufferedImage src, int x, int y, int width, int height) {
        BufferedImage dest = new BufferedImage(width, height, src.getType());
        pasteInto(dest, src, -x, -y);
        return dest;
    }

    /**
     * Concatenates the images left to right. Images are top-aligned, and the background
     * is initialized to black.
     *
     * @param layers images to concatenate.
     * @return a new image with all source images concatenated left to right.
     */
    public static BufferedImage leftToRight(BufferedImage ... layers) {
        int width = 0;
        int height = 0;

        for (BufferedImage layer : layers) {
            width += layer.getWidth();
            height = Math.max(height, layer.getHeight());
        }

        BufferedImage dest = new BufferedImage(width, height, layers[0].getType());
        Graphics2D g = createGraphics(dest);
        g.setBackground(Color.BLACK);
        g.clearRect(0, 0, width, height);
        g.dispose();

        width = 0;
        for (BufferedImage layer : layers) {
            pasteInto(dest, layer, width, 0);
            width += layer.getWidth();
        }

        return dest;
    }

    /**
     * Concatenates the images top to bottom. Images are left-aligned, and the background
     * is initialized to black.
     *
     * @param layers images to concatenate.
     * @return a new image with all source images concatenated top to bottom.
     */
    public static BufferedImage topToBottom(BufferedImage ... layers) {
        int width = 0;
        int height = 0;

        for (BufferedImage layer : layers) {
            width = Math.max(width, layer.getWidth());
            height += layer.getHeight();
        }

        BufferedImage dest = new BufferedImage(width, height, layers[0].getType());
        Graphics2D g = createGraphics(dest);
        g.setBackground(Color.BLACK);
        g.clearRect(0, 0, width, height);
        g.dispose();

        height = 0;
        for (BufferedImage layer : layers) {
            pasteInto(dest, layer, 0, height);
            height += layer.getHeight();
        }

        return dest;
    }

    /**
     * Returns an image with the top image pasted onto the bottom image at the specified
     * location.
     *
     * @param bottom image to draw on the bottom (below the top image).
     * @param top image to draw on top of the bottom image.
     * @param x x coordinate (in bottom image) of where to paste the top image.
     * @param y y coordinate (in bottom image) of where to paste the top image.
     * @return a copy of the bottom image with the top image pasted at x and y.
     */
    public static BufferedImage pasteAt(BufferedImage bottom, BufferedImage top, int x, int y) {
        BufferedImage dest = copy(bottom);

        pasteInto(dest, top, x, y);

        return dest;
    }

    /**
     * Returns an image with the top image pasted onto the bottom image at the specified
     * location and with the specified blending mode.
     *
     * @param bottom image to draw on the bottom (below the top image).
     * @param top image to draw on top of the bottom image.
     * @param x x coordinate (in bottom image) of where to paste the top image.
     * @param y y coordinate (in bottom image) of where to paste the top image.
     * @param blendingMode the mode to use for blending the top image. Can be
     * NORMAL or SCREEN.
     * @return a copy of the bottom image with the top image pasted at x and y.
     */
    public static BufferedImage pasteAtWith(BufferedImage bottom, BufferedImage top, int x, int y,
            BlendingMode blendingMode) {

        BufferedImage dest;

        switch (blendingMode) {
            case NORMAL:
                dest = copy(bottom);
                pasteInto(dest, top, x, y);
                break;

            case SCREEN:
                dest = copy(bottom);
                Graphics2D g = createGraphics(dest);
                for (int dy = 0; dy < top.getHeight(); dy++) {
                    for (int dx = 0; dx < top.getWidth(); dx++) {
                        int bottomRgb = bottom.getRGB(x + dx, y + dy);
                        int topRgb = top.getRGB(dx, dy);

                        int bottomRed = bottomRgb & 0xFF;
                        int bottomGreen = (bottomRgb >> 8) & 0xFF;
                        int bottomBlue = (bottomRgb >> 16) & 0xFF;
                        // Ignore bottom alpha?
                        /// int bottomAlpha = (bottomRgb >> 24) & 0xFF;

                        int topRed = topRgb & 0xFF;
                        int topGreen = (topRgb >> 8) & 0xFF;
                        int topBlue = (topRgb >> 16) & 0xFF;
                        // Ignoring top alpha until we need it.
                        /// int topAlpha = (topRgb >> 24) & 0xFF;

                        int resultRed = 255 - (255 - bottomRed)*(255 - topRed)/255;
                        int resultGreen = 255 - (255 - bottomGreen)*(255 - topGreen)/255;
                        int resultBlue = 255 - (255 - bottomBlue)*(255 - topBlue)/255;
                        int resultAlpha = 255;

                        int resultRgb = (resultAlpha << 24) | (resultBlue << 16)
                            | (resultGreen << 8) | resultRed;

                        dest.setRGB(x + dx, y + dy, resultRgb);
                    }
                }
                g.dispose();
                break;

            default:
                throw new IllegalArgumentException("Must specify a blending mode.");
        }

        return dest;
    }

    /**
     * Converts a color image to grayscale. Keeps the alpha.
     *
     * @param image source image.
     * @return a copy of the source, but in grayscale.
     */
    public static BufferedImage toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();

        image = copy(image);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                int red = rgb & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = (rgb >> 16) & 0xFF;
                int alpha = (rgb >> 24) & 0xFF;

                int gray = (red*30 + green*59 + blue*11)/100;

                rgb = (alpha << 24) | (gray << 16) | (gray << 8) | gray;

                image.setRGB(x, y, rgb);
            }
        }

        return image;
    }

    /**
     * Finds the edges (sharp changes) within an image. Copies the input alpha
     * channel, if any.
     *
     * @param input source image.
     * @return a grayscale image of the edges of the red channel of the input
     * image.
     */
    public static BufferedImage findEdges(BufferedImage input) {
        int width = input.getWidth();
        int height = input.getHeight();
        int pixelCount = width*height;
        int bytesPerPixel = getBytesPerPixel(input);

        BufferedImage output = copy(input);

        byte[] inputData = ((DataBufferByte) input.getRaster().getDataBuffer()).getData();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = input.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;

                // 3x3 neighbors in row-major order.
                byte[] n = getNeighbors(inputData, x, y, width, height, bytesPerPixel);

                // http://www.emanueleferonato.com/2010/10/19/
                //      image-edge-detection-algorithm-php-version/
                int gx = n[0]*-1 + n[2] + n[3]*-2 + n[5]*2 + n[6]*-1 + n[8];
                int gy = n[0]*-1 + n[1]*-2 + n[2]*-1 + n[6] + n[7]*2 + n[8];

                // Use Manhattan distance.
                if (gx < 0) {
                    gx = -gx;
                }
                if (gy < 0) {
                    gy = -gy;
                }
                int gray = gx + gy;

                rgb = (alpha << 24) | (gray << 16) | (gray << 8) | gray;

                output.setRGB(x, y, rgb);
            }
        }

        return output;
    }

    /**
     * Return row-major list of neighboring values (3x3). Returns 0 values for off the image.
     */
    private static byte[] getNeighbors(byte[] inputData, int cx, int cy,
            int width, int height, int bytesPerPixel) {

        byte[] neighbors = new byte[9];

        int lowX = Math.max(cx - 1, 0);
        int lowY = Math.max(cy - 1, 0);
        int highX = Math.min(cx + 1, width - 1);
        int highY = Math.min(cy + 1, height - 1);

        for (int y = lowY; y <= highY; y++) {
            for (int x = lowX; x <= highX; x++) {
                int index = (y - cy + 1)*3 + (x - cx + 1);
                neighbors[index] = inputData[(y*width + x)*bytesPerPixel];
            }
        }

        return neighbors;
    }

    /**
     * Returns an inverted images. The alpha mask is untouched, if it's there.
     *
     * @param image source image.
     * @return an inverted copy of the source image (black is white, white is black, etc.).
     */
    public static BufferedImage invert(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width*height;
        int bytesPerPixel = getBytesPerPixel(image);

        image = copy(image);

        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        int index = pixelCount - 3;
        for (int i = 0; i < pixelCount; i++) {
            // Skip alpha, if any.
            data[index + 0] = (byte) (255 - ((int) data[index + 0] & 0xFF));
            data[index + 1] = (byte) (255 - ((int) data[index + 1] & 0xFF));
            data[index + 2] = (byte) (255 - ((int) data[index + 2] & 0xFF));

            index += bytesPerPixel;
        }

        return image;
    }

    /**
     * Returns an image with inverted alpha. Must have an alpha channel.
     *
     * @param image source image.
     * @return a copy with the alpha inverted (transparent is opaque and vice versa).
     */
    public static BufferedImage invertAlpha(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width*height;
        int bytesPerPixel = getBytesPerPixel(image);
        if (bytesPerPixel != 4) {
            throw new IllegalArgumentException("image must have alpha to be inverted");
        }

        image = copy(image);

        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        int index = 0;
        for (int i = 0; i < pixelCount; i++) {
            data[index] = (byte) (255 - ((int) data[index] & 0xFF));
            index += bytesPerPixel;
        }

        return image;
    }

    /**
     * Sets the image to the grayscale value of each pixel snapped to the nearest
     * valid value. Uses the red channel for input. The alpha is untouched.
     *
     * @param image source image.
     * @param values possible grayscale values (0 to 255) to allow. Must
     * be ordered.
     * @return a grayscale version of the original, with only the allowed values.
     */
    public static BufferedImage quantize(BufferedImage image, int[] values) {
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width*height;
        int bytesPerPixel = getBytesPerPixel(image);

        image = copy(image);

        byte[] data = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();

        // Create lookup table for snapping.
        int[] snapValue = new int[256];
        int k = 0;
        for (int i = 0; i < values.length - 1; i++) {
            int average = (values[i] + values[i + 1]) / 2;
            while (k < average) {
                snapValue[k++] = values[i];
            }
        }
        while (k < snapValue.length) {
            snapValue[k++] = values[values.length - 1];
        }

        // Snap each pixel.
        int index = 0;
        for (int i = 0; i < pixelCount; i++) {
            int gray = (int) data[index + 0] & 0xFF;
            gray = snapValue[gray];

            data[index + 0] = (byte) gray;
            data[index + 1] = (byte) gray;
            data[index + 2] = (byte) gray;
            // Skip alpha, if any.

            index += bytesPerPixel;
        }

        return image;
    }

    /**
     * Sets the image to one of the textures specified depending on the value
     * of each pixel snapped to the nearest valid value. The red channel
     * is used for input. The alpha is untouched.
     *
     * @param image source image.
     * @param values possible grayscale values (0 to 255) to snap the red channel to. Must
     * be ordered.
     * @param textures textures to use when the red channel of a pixel snaps to a
     * value in in "values". Must be the same length as "values".
     * @return a composite image of the textures, based on the textures.
     */
    public static BufferedImage quantizeTexture(BufferedImage image, int[] values,
            BufferedImage[] textures) {

        int width = image.getWidth();
        int height = image.getHeight();
        int pixelCount = width*height;
        int bytesPerPixel = getBytesPerPixel(image);

        image = copy(image);

        byte[] inputData = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        byte[][] texturesData = new byte[textures.length][];
        for (int i = 0; i < textures.length; i++) {
            texturesData[i] = ((DataBufferByte) textures[i].getRaster().getDataBuffer()).getData();
        }

        // Create lookup table for snapping.
        int[] snapIndex = new int[256];
        int k = 0;
        for (int i = 0; i < values.length - 1; i++) {
            int average = (values[i] + values[i + 1]) / 2;
            while (k < average) {
                snapIndex[k++] = i;
            }
        }
        while (k < snapIndex.length) {
            snapIndex[k++] = values.length - 1;
        }

        // Snap each pixel.
        int index = 0;
        for (int i = 0; i < pixelCount; i++) {
            int gray = (int) inputData[index] & 0xFF;
            int textureIndex = snapIndex[gray];
            byte[] textureData = texturesData[textureIndex];

            inputData[index + 0] = textureData[index + 0];
            inputData[index + 1] = textureData[index + 1];
            inputData[index + 2] = textureData[index + 2];
            // Skip alpha, if any.

            index += bytesPerPixel;
        }

        return image;
    }

    /**
     * Returns an image with the color of the main image (which must be BGR) and the
     * alpha of the mask (which must be ABGR, color is ignored). The two images must
     * be the same size.
     *
     * @param image source BGR image.
     * @param mask source ABGR image for the mask. Its color is ignored.
     * @return the source image masked to the alpha of the mask.
     */
    public static BufferedImage clipToMask(BufferedImage image, BufferedImage mask) {
        assert image.getType() == BufferedImage.TYPE_3BYTE_BGR;
        assert mask.getType() == BufferedImage.TYPE_4BYTE_ABGR;
        assert image.getWidth() == mask.getWidth();
        assert image.getHeight() == mask.getHeight();

        BufferedImage output = copy(mask);

        int width = image.getWidth();
        int height = image.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int imageRgb = image.getRGB(x, y);
                int outputRgb = output.getRGB(x, y);

                outputRgb = (outputRgb & 0xFF000000) | (imageRgb & 0x00FFFFFF);

                output.setRGB(x, y, outputRgb);
            }
        }

        return output;
    }

    /**
     * Returns an semi-transparent image where the transparency was deduced from the color.
     *
     * @param image source image.
     * @param grayTransparent gray value (0 to 255) to use for fully transparent.
     * @param grayOpaque gray value (0 to 255) to use for fully opaque.
     * @return a copy of the source image where the transparency is based on
     * the grayscale value of each pixel.
     */
    public static BufferedImage grayToTransparent(BufferedImage image,
            int grayTransparent, int grayOpaque) {

        int width = image.getWidth();
        int height = image.getHeight();

        log("Converting grayscale to transparent (%dx%d), %d to %d", width, height,
                grayTransparent, grayOpaque);

        assert image.getType() == BufferedImage.TYPE_3BYTE_BGR;

        BufferedImage output = makeTransparent(width, height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);

                int red = rgb & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = (rgb >> 16) & 0xFF;

                int gray = (red*30 + green*59 + blue*11)/100;

                int opacity = (gray - grayTransparent) * 255 / (grayOpaque - grayTransparent);

                rgb = (opacity << 24) | (rgb & 0x00FFFFFF);

                output.setRGB(x, y, rgb);
            }
        }

        return output;

    }

    /**
     * Draws src into dest at x, y.
     */
    private static void pasteInto(BufferedImage dest, BufferedImage src, int x, int y) {
        Graphics2D g = createGraphics(dest);
        g.drawImage(src, x, y, null);
        g.dispose();
    }

    /**
     * Interpolates two colors.
     *
     * @param c1 one color.
     * @param c2 the other color.
     * @param fraction how much of c2 to have. 0.0 means c1 and 1.0 means c2. Values outside
     * the range 0.0 to 1.1 will be clamped at 0 and 255 (possibly causing distortion).
     * @return a color between c1 and c2.
     */
    public static Color interpolateColor(Color c1, Color c2, double fraction) {
        int c1R = c1.getRed();
        int c1G = c1.getGreen();
        int c1B = c1.getBlue();
        int c2R = c2.getRed();
        int c2G = c2.getGreen();
        int c2B = c2.getBlue();
        int diffR = c2R - c1R;
        int diffG = c2G - c1G;
        int diffB = c2B - c1B;

        int r = (int) (c1R + fraction*diffR);
        int g = (int) (c1G + fraction*diffG);
        int b = (int) (c1B + fraction*diffB);

        r = Math.min(Math.max(r, 0), 255);
        g = Math.min(Math.max(g, 0), 255);
        b = Math.min(Math.max(b, 0), 255);

        return new Color(r, g, b);
    }

    /**
     * Generate a checkerboard image. The squares are anchored at the upper-left
     * corner of the image.
     *
     * @param width the width of the generated image
     * @param height the height of the generated image
     * @param color1 the color of the square in the upper-left corner
     * @param color2 the alternating color
     * @param squareSize the width and height of the checker square
     * @return a checkerboard image.
     */
    public static BufferedImage makeCheckerboard(int width, int height,
            Color color1, Color color2, int squareSize) {

        BufferedImage image = make(width, height, color1);

        Graphics2D g = createGraphics(image);
        g.setPaint(color2);

        for (int y = 0; y < height; y += squareSize) {
            for (int x = y % (squareSize*2); x < width; x += squareSize*2) {
                g.fillRect(x, y, squareSize, squareSize);
            }
        }

        g.dispose();

        return image;
    }

    /**
     * Compose the given image over a checkerboard to look for the transparency area.
     *
     * @param image source image.
     * @return the source image over a checkerboard of the same size.
     */
    public static BufferedImage composeOverCheckerboard(BufferedImage image) {
        BufferedImage checkerboard = makeCheckerboard(
                image.getWidth(), image.getHeight(),
                new Color(0.65f, 0.65f, 0.65f, 1.0f),
                new Color(0.70f, 0.70f, 0.70f, 1.0f),
                16);

        return compose(checkerboard, image);
    }

    /**
     * Load an image from a filename.
     *
     * @param filename the pathname of the image file to load.
     * @return the loaded image.
     * @throws IOException if there's a problem loading the image.
     */
    public static BufferedImage load(String filename) throws IOException {
        BufferedImage image = ImageIO.read(new File(filename));

        // I don't know why this happens, and whether it's okay to always go to BGR.
        if (image.getType() == 0) {
            image = convertType(image, BufferedImage.TYPE_3BYTE_BGR);
        }

        log("Loaded \"%s\" (%dx%d)", filename, image.getWidth(), image.getHeight());

        return image;
    }

    /**
     * Load an image from an input stream.
     *
     * @param inputStream the input stream of the image file to load.
     * @return the loaded image.
     * @throws IOException if there's a problem loading the image.
     */
    public static BufferedImage load(InputStream inputStream) throws IOException {
        BufferedImage image = ImageIO.read(inputStream);

        // I don't know why this happens, and whether it's okay to always go to BGR.
        if (image.getType() == 0) {
            image = convertType(image, BufferedImage.TYPE_3BYTE_BGR);
        }

        log("Loaded input stream (%dx%d)", image.getWidth(), image.getHeight());

        return image;
    }

    /**
     * Saves an image to a filename, auto-detecting the type.
     *
     * @param image source image.
     * @param filename the pathname to save the file to. The extension is used
     * for the file type. Supports PNG (.png) and JPEG (.jpg or .jpeg) files.
     * @throws IOException if there's a problem saving the image.
     */
    public static void save(BufferedImage image, String filename) throws IOException {
        log("Saving \"%s\" (%dx%d)", filename, image.getWidth(), image.getHeight());

        String fileType;

        if (filename.toLowerCase().endsWith(".png")) {
            fileType = "png";
        } else if (filename.toLowerCase().endsWith(".jpg")
                || filename.toLowerCase().endsWith(".jpeg")) {

            fileType = "jpg";
        } else {
            throw new IllegalArgumentException("File type not supported: " + filename);
        }

        ImageIO.write(image, fileType, new File(filename));
    }

    /**
     * Saves an image to an output stream, auto-detecting the type from the MIME type.
     *
     * @param image source image.
     * @param outputStream the output stream to save the file to.
     * @param mimeType the type of the image. Use "image/png" for PNG and either
     * "image/jpg" or "image/jpeg" for JPEG.
     * @throws IOException if there's a problem saving the image.
     */
    public static void save(BufferedImage image, OutputStream outputStream, String mimeType)
        throws IOException {

        log("Saving output stream of type \"%s\" (%dx%d)", mimeType,
                image.getWidth(), image.getHeight());

        String fileType;

        if (mimeType.toLowerCase().equals("image/png")) {
            fileType = "png";
        } else if (mimeType.toLowerCase().equals("image/jpg")
                || mimeType.toLowerCase().equals("image/jpeg")) {

            fileType = "jpg";
        } else {
            throw new IllegalArgumentException("Mime type not supported: " + mimeType);
        }

        ImageIO.write(image, fileType, outputStream);
    }

    /**
     * Saves a list of images as an animated GIF with the specified pause time for each
     * image and whether to loop continuously. The filename must end with ".gif".
     *
     * Based on code from http://elliot.kroo.net/software/java/GifSequenceWriter/
     *
     * @param images source images.
     * @param filename pathname of GIF to save to.
     * @param frameTimeMs number of milliseconds to show each frame.
     * @param loop whether to loop the animation.
     * @throws IOException if there's a problem saving the image.
     */
    public static void saveAnimatedGif(List<BufferedImage> images, String filename,
            int frameTimeMs, boolean loop) throws IOException {

        if (images.isEmpty()) {
            throw new IllegalArgumentException("Animated GIF must have at least one image");
        }

        if (!filename.toLowerCase().endsWith(".gif")) {
            throw new IllegalArgumentException("Output filename must end with .gif");
        }

        log("Saving animated GIF \"%s\" (%dx%d, %d frames, %d ms delay, %slooping)", filename,
                images.get(0).getWidth(), images.get(0).getHeight(), images.size(),
                frameTimeMs, loop ? "" : "not ");

        // Find a GIF writer.
        ImageWriter writer;
        Iterator<ImageWriter> itr = ImageIO.getImageWritersBySuffix("gif");
        if (!itr.hasNext()) {
            // Can't happen, it's included in Java.
            throw new IllegalStateException("No GIF image writers found");
        } else {
            // Pick the first one.
            writer = itr.next();
        }

        // Determine the image type from the first image.
        int imageType = images.get(0).getType();

        // Round frame time to nearest centisecond.
        int frameTimeCs = (frameTimeMs + 5) / 10;

        // Output file.
        ImageOutputStream output = new FileImageOutputStream(new File(filename));

        // Set up the write parameters. We'll use these for every frame.
        ImageWriteParam imageWriteParam = writer.getDefaultWriteParam();
        ImageTypeSpecifier imageTypeSpecifier =
            ImageTypeSpecifier.createFromBufferedImageType(imageType);

        IIOMetadata imageMetaData = writer.getDefaultImageMetadata(imageTypeSpecifier,
                imageWriteParam);

        String metaFormatName = imageMetaData.getNativeMetadataFormatName();

        IIOMetadataNode root = (IIOMetadataNode) imageMetaData.getAsTree(metaFormatName);

        // Basic parameters.
        IIOMetadataNode graphicControlExtensionNode = getChild(root, "GraphicControlExtension");
        graphicControlExtensionNode.setAttribute("disposalMethod", "none");
        graphicControlExtensionNode.setAttribute("userInputFlag", "FALSE");
        graphicControlExtensionNode.setAttribute("transparentColorFlag", "FALSE");
        graphicControlExtensionNode.setAttribute("delayTime", Integer.toString(frameTimeCs));
        graphicControlExtensionNode.setAttribute("transparentColorIndex", "0");

        IIOMetadataNode commentNode = getChild(root, "CommentExtensions");
        commentNode.setAttribute("CommentExtension", "Created by the Team Ten Library");

        IIOMetadataNode applicationEntensionsNode = getChild(root, "ApplicationExtensions");

        // Set looping.
        IIOMetadataNode applicationExtensionNode = new IIOMetadataNode("ApplicationExtension");

        applicationExtensionNode.setAttribute("applicationID", "NETSCAPE");
        applicationExtensionNode.setAttribute("authenticationCode", "2.0");

        int loopBit = loop ? 0 : 1;

        applicationExtensionNode.setUserObject(new byte[] {
            0x1,
            (byte) (loopBit & 0xFF),
            (byte) ((loopBit >> 8) & 0xFF)
        });
        applicationEntensionsNode.appendChild(applicationExtensionNode);

        imageMetaData.setFromTree(metaFormatName, root);

        writer.setOutput(output);
        writer.prepareWriteSequence(null);

        try {
            // Write each of the images.
            for (BufferedImage image : images) {
                // No thumbnails.
                IIOImage iioImage = new IIOImage(image, null, imageMetaData);
                writer.writeToSequence(iioImage, imageWriteParam);
            }
        } finally {
            writer.endWriteSequence();
            output.close();
        }
    }

    /**
     * Returns an existing child node, or creates and returns a new child node if
     * the requested node does not exist.
     */
    private static IIOMetadataNode getChild(IIOMetadataNode parent, String name) {
        // Search for existing node.
        for (int i = 0; i < parent.getLength(); i++) {
            Node child = parent.item(i);

            if (child.getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) child;
            }
        }

        // Not found. Create new child node.
        IIOMetadataNode node = new IIOMetadataNode(name);
        parent.appendChild(node);

        return node;
    }

    /**
     * Returns an object setting as many things to high-quality as possible.
     *
     * @return a rendering hints object with high-quality settings.
     */
    public static RenderingHints getHighQualityRenderingHints() {
        return new RenderingHints(getHighQualityRenderingMap());
    }

    /**
     * Returns a map setting as many things to high-quality as possible.
     *
     * @return a map setting as many things to high-quality as possible.
     */
    public static Map<RenderingHints.Key,Object> getHighQualityRenderingMap() {
        Map<RenderingHints.Key,Object> map = new HashMap<RenderingHints.Key,Object>();

        map.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        map.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        map.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        map.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        map.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
        // This is only supposed to control the spacing and position of characters, but actually
        // makes them look good. Maybe it forces each glyph to be rendered again and it's
        // rendered better?
        map.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

        return map;
    }

    /**
     * Find a font. The font file must be in the JAR's resources directory.
     *
     * @param typeface the typeface of the font.
     * @param bold whether the font should be bold.
     * @param italic whether the font should be italic.
     * @param narrow whether the font should be narrow.
     * @param size the font size. It's not clear from the Java SDK's documentation
     * what unit this value is.
     * @return a font of the specified typeface and attributes and size.
     * @throws FontFormatException if the file is not of a correct font format.
     * @throws IOException if there's a problem loading the file.
     */
    public static Font getFont(Typeface typeface, boolean bold, boolean italic, boolean narrow,
            double size) throws FontFormatException, IOException {

        // Figure out which filename to load.
        String filename = null;
        File file = null;

        switch (typeface) {
            case HELVETICA:
                int helveticaNumber = 45
                    + (italic ? 1 : 0)
                    + (narrow ? 2 : 0)
                    + (bold ? 20 : 0);

                filename = "fonts/helr" + helveticaNumber + "w.ttf";
                break;

            case FUTURA:
                /**
                    Florencesans:
                    1 = roman
                    2 = ? (slightly smaller)
                    3 = italic
                    4 = italic lighter
                    5 = very compressed
                    6, 7, 8 = ditto
                    9 = slightly compressed
                    10, 11, 12 = ditto
                    13 = extended
                    14, 15, 16 = ditto
                    +16 = small caps
                    33 = bold
                    34 = bold italic
                    35-36 = small caps
                    37 = outline
                    38 = outline italic
                    39-40 = small caps
                    41 = shadow
                    42 = shadow italic
                    43-44 = small caps
                    45 = reverse italic
                    46 = reverse italic slightly smaller
                */

                int futuraNumber;
                if (bold) {
                    futuraNumber = 33 + (italic ? 1 : 0);
                } else {
                    futuraNumber = 1
                        + (italic ? 2 : 0)
                        + (narrow ? 8 : 0);
                }

                filename = String.format("fonts/Florsn%02d.ttf", futuraNumber);
                break;

            case COURIER:
                if (bold && italic) {
                    filename = "fonts/courbi.ttf";
                } else if (bold) {
                    filename = "fonts/courbd.ttf";
                } else if (italic) {
                    filename = "fonts/couri.ttf";
                } else {
                    filename = "fonts/cour.ttf";
                }
                break;

            case GARAMOND:
                if (bold && italic) {
                    filename = "fonts/Adobe Garamond Pro Bold Italic.ttf";
                } else if (bold) {
                    filename = "fonts/Adobe Garamond Pro Bold.ttf";
                } else if (italic) {
                    filename = "fonts/Adobe Garamond Pro Italic.ttf";
                } else {
                    filename = "fonts/Adobe Garamond Pro.ttf";
                }
                break;

            case MINION:
                if (bold && italic) {
                    throw new IllegalArgumentException("bold italic Minion not supported");
                } else if (bold) {
                    throw new IllegalArgumentException("bold Minion not supported");
                } else if (italic) {
                    file = new File(MY_FONT_DIR, "Minion/MinionPro-It.ttf");
                } else {
                    file = new File(MY_FONT_DIR, "Minion/MinionPro-Regular.ttf");
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown typeface " + typeface);
        }

        InputStream inputStream;
        if (file != null) {
            log("Loading font \"%s\"", file);
            inputStream = new BufferedInputStream(new FileInputStream(file));
        } else {
            log("Loading font \"%s\"", filename);
            inputStream = ImageUtils.class.getResourceAsStream(filename);
            if (inputStream == null) {
                throw new FileNotFoundException("cannot find font file " + filename);
            }
        }

        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont((float) size);
        } finally {
            inputStream.close();
        }

        // Turn on kerning. This doesn't seem to make any difference.
        Map<AttributedCharacterIterator.Attribute,Object> attributes =
            new HashMap<AttributedCharacterIterator.Attribute,Object>();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        font = font.deriveFont(attributes);

        return font;
    }

    /**
     * Return a font from the specified filename and size. If the
     * filename is relative, then it will be relative to $HOME/fonts.
     *
     * @param filename the pathname to the font file. Must be a TrueType font.
     * @param size the font size. It's not clear from the Java SDK's documentation
     * what unit this value is.
     * @return the loaded font.
     * @throws FontFormatException if the file is not of a correct font format.
     * @throws IOException if there's a problem loading the file.
     */
    public static Font getFont(String filename, double size)
        throws FontFormatException, IOException {

        if (!filename.startsWith("/")) {
            filename = FONT_DIR + File.separator + filename;
        }

        log("Loading font \"%s\" at size %g", filename, size);

        InputStream inputStream = new FileInputStream(filename);

        Font font;
        try {
            font = Font.createFont(Font.TRUETYPE_FONT, inputStream).deriveFont((float) size);
        } finally {
            inputStream.close();
        }

        // Turn on kerning. This doesn't seem to make any difference.
        Map<AttributedCharacterIterator.Attribute,Object> attributes =
            new HashMap<AttributedCharacterIterator.Attribute,Object>();
        attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
        font = font.deriveFont(attributes);

        return font;
    }

    /**
     * Set the PrintStream for log messages. Defaults to System.out.
     * Use null to suppress logging.
     */
    public static void setLogger(PrintStream out) {
        mLogger = out;
    }

    /**
     * Prints a formatted line to the logger.
     */
    static void log(String format, Object... args) {
        if (mLogger != null) {
            mLogger.println(String.format(format, args));
        }
    }
}

