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

// Copyright 2010 Lawrence Kesteloot.

package com.teamten.image;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

/**
 * Like ConvolveOp, this convolves a kernel with an image. But this class assumes that
 * the filter is linearly decomposable, and performs the operation in two passes.
 */
public class DecomposableConvolveOp extends AbstractBufferedImageOp {
    // Roughly approximate monitors.
    private static final double GAMMA = 2.2;
    private static final double[] GAMMA_TO_LINEAR;
    private final double[] mKernel;

    static {
        GAMMA_TO_LINEAR = new double[256];
        for (int i = 0; i < 256; i++) {
            GAMMA_TO_LINEAR[i] = Math.pow(i/255.0, GAMMA);
        }
    }

    /**
     * Converts a gamma-encoded value between 0 and 255 to a linear
     * value between 0.0 and 1.0. The gamma is in the GAMMA constant.
     */
    private static double gammaToLinear(int linearValue) {
        return GAMMA_TO_LINEAR[linearValue];
    }

    /**
     * Converts a linear value between 0.0 and 1.0 to a gamma-encoded
     * value between 0 and 255. The gamma is in the GAMMA constant.
     */
    private static int linearToGamma(double gammaValue) {
        // Not worth doing a look-up table for this, it's only called once per
        // output pixel. On a 1024x1024 image it would save at most 20ms.
        int linearValue = (int) (Math.pow(gammaValue, 1/GAMMA)*255.9);

        // Clamp.
        return Math.min(Math.max(linearValue, 0), 255);
    }

    /**
     * The kernel is the horizontal or vertical cross-section of the 2D kernel
     * through its center.
     *
     * @param kernel the convolution kernel. Must have an odd length.
     */
    public DecomposableConvolveOp(double[] kernel) {
        assert kernel.length % 2 != 0;

        mKernel = kernel;
    }

    @Override // BufferedImageOp
    public BufferedImage filter(BufferedImage src, BufferedImage dest) {
        // Just assert, we don't want to silently convert.
        assert src.getType() == BufferedImage.TYPE_3BYTE_BGR
            || src.getType() == BufferedImage.TYPE_4BYTE_ABGR;

        // Apply the kernel twice, once in each direction. Each convolve() call both
        // applies the kernel horizontally and transposes the image.
        return convolve(convolve(src));
    }

    /**
     * Convolves horizontally and transposes the output image.
     */
    private BufferedImage convolve(BufferedImage src) {
        int bytesPerPixel = src.getColorModel().getNumComponents();

        int width = src.getWidth();
        int height = src.getHeight();

        // Make a destination image of the swapped dimensions.
        BufferedImage dest = new BufferedImage(height, width, src.getType());

        // Get the raw underlying bytes. In principle this might break if the
        // data buffer is in multiple banks.
        byte[] srcData = ((DataBufferByte) src.getRaster().getDataBuffer()).getData();
        byte[] destData = ((DataBufferByte) dest.getRaster().getDataBuffer()).getData();

        // Verify that it's the size we expect.
        assert width*height*bytesPerPixel == srcData.length;
        assert height*width*bytesPerPixel == destData.length;

        // The number of bytes per row.
        int srcStride = width*bytesPerPixel;
        int destStride = height*bytesPerPixel;

        // Assume the kernel length is odd.
        int filterRadius = (mKernel.length - 1) / 2;

        // Go through every row of the source image.
        for (int yy = 0; yy < height; yy++) {
            // Go through every pixel of the source row.
            for (int xx = 0; xx < width; xx++) {
                // For every color component.
                for (int b = 0; b < bytesPerPixel; b++) {
                    // Apply the convolution.
                    double sum = 0.0;
                    for (int i = 0; i < mKernel.length; i++) {
                        // Position in source image.
                        int x = xx - filterRadius + i;

                        // Treat off-image pixels as their closest pixel in the image.
                        int clampedX;
                        if (x < 0) {
                            clampedX = 0;
                        } else if (x > width - 1) {
                            clampedX = width - 1;
                        } else {
                            clampedX = x;
                        }

                        int byteIndex = yy*srcStride + clampedX*bytesPerPixel + b;
                        int pixel = (int) srcData[byteIndex] & 0xFF;
                        sum += gammaToLinear(pixel) * mKernel[i];
                    }

                    // Clamp color for writing to byte.
                    int result = linearToGamma(sum);

                    // Write transposed into destination image.
                    destData[xx*destStride + yy*bytesPerPixel + b] = (byte) result;
                }
            }
        }

        return dest;
    }

    /**
     * Returns a Gaussian kernel of the specified radius, where the radius represents
     * one sigma (standard deviation).
     *
     * @param radius one sigma of the gaussian kernel.
     * @return a gaussian kernel with the specified sigma.
     */
    public static double[] makeGaussianKernel(double radius) {
        // This matches Photoshop.
        double sigma = radius;

        // Go to 3 sigma, where it's pretty much zero.
        int arrayHalfSize = (int) Math.ceil(radius*3);
        int arraySize = arrayHalfSize*2 + 1;
        double[] kernel = new double[arraySize];

        // Precompute constant.
        double twoSigmaSquared = 2*sigma*sigma;

        // Keep track of total so we can normalize.
        double total = 0;
        int index = 0;

        // Walk the kernel.
        for (int i = -arrayHalfSize; i <= arrayHalfSize; i++) {
            // Distance from center.
            double distanceSquared = i*i;

            // Don't bother dividing by constant, we normalize anyway.
            double value = Math.exp(-distanceSquared / twoSigmaSquared);
            kernel[index] = value;

            total += value;
            index++;
        }

        // Normalize so area is 1.0.
        for (int i = 0; i < arraySize; i++) {
            kernel[i] /= total;
        }

        return kernel;
    }
}
