package com.teamten.image;

/**
 * Utility methods for dealing with color values. Doubles are always assumed
 * to be linear and in the range 0.0 to 1.0. Bytes and ints are always assumed
 * to be in the range 0 to 255, but could be either linear or gamma.
 */
public class ColorUtils {
    private static final double GAMMA = 2.2;
    private static final double INV_GAMMA = 1.0/GAMMA;
    private static final double[] GAMMA_TO_LINEAR;

    static {
        // Compute gamma table.
        GAMMA_TO_LINEAR = new double[256];
        for (int i = 0; i < 256; i++) {
            GAMMA_TO_LINEAR[i] = Math.pow(i/255.0, GAMMA);
        }
    }

    /**
     * Converts a gamma-encoded value between 0 and 255 to a linear
     * value between 0.0 and 1.0. The gamma is in the GAMMA constant.
     */
    public static double gammaIntToDouble(int gammaValue) {
        return GAMMA_TO_LINEAR[gammaValue];
    }

    /**
     * Converts a gamma-encoded value between 0 and 255 to a linear
     * value between 0.0 and 1.0. The gamma is in the GAMMA constant.
     */
    public static double gammaByteToDouble(byte gammaValue) {
        return gammaIntToDouble(byteToInt(gammaValue));
    }

    /**
     * Converts a linear byte value to a linear double between 0.0 and 1.0.
     */
    public static double linearByteToDouble(byte linearValue) {
        return byteToInt(linearValue)/255.0;
    }

    /**
     * Converts a linear value between 0.0 and 1.0 to a gamma-encoded
     * value between 0 and 255. The gamma is in the GAMMA constant.
     */
    public static int doubleToGammaInt(double linearValue) {
        // Not worth doing a look-up table for this, it's only called once per
        // output pixel. On a 1024x1024 image it would save at most 20ms.
        return doubleToLinearInt(Math.pow(linearValue, INV_GAMMA));
    }

    /**
     * Converts a linear value between 0.0 and 1.0 to a gamma-encoded
     * value between 0 and 255. The gamma is in the GAMMA constant.
     */
    public static byte doubleToGammaByte(double linearValue) {
        return (byte) doubleToGammaInt(linearValue);
    }

    /**
     * Convert a color value from 0.0 to 1.0 to an int without doing
     * any gamma conversion. This is useful for alpha values.
     */
    public static int doubleToLinearInt(double d) {
        return Math.min(Math.max((int) (d*255.9), 0), 255);
    }

    /**
     * Convert a color value from 0.0 to 1.0 to a byte without doing
     * any gamma conversion. This is useful for alpha values.
     */
    public static byte doubleToLinearByte(double d) {
        return (byte) doubleToLinearInt(d);
    }

    /**
     * Bytes are signed in Java, so even if we take them to mean 0 to 255, we can't use their
     * values directly, since values 128 to 255 will show up as negative. This method converts
     * the byte to an int with the values 0 to 255.
     */
    public static int byteToInt(byte b) {
        return (int) b & 0xFF;
    }
}
