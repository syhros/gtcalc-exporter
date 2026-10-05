package com.syhros.packextract.util;

/** Colour helpers. */
public final class Colors {

    private Colors() {}

    /** 0xAARRGGBB or 0xRRGGBB to "#rrggbb" (alpha dropped). */
    public static String hex(int rgb) {
        String h = Integer.toHexString(rgb & 0xFFFFFF);
        StringBuilder sb = new StringBuilder("#");
        for (int i = h.length(); i < 6; i++) {
            sb.append('0');
        }
        return sb.append(h).toString();
    }

    /**
     * Converts bottom-up RGBA bytes from glReadPixels into top-down ARGB ints and returns how many pixels are not
     * fully transparent.
     */
    public static int rgbaToArgb(byte[] rgba, int size, int[] argb) {
        int opaque = 0;
        for (int y = 0; y < size; y++) {
            int src = (size - 1 - y) * size * 4;
            int dst = y * size;
            for (int x = 0; x < size; x++) {
                int r = rgba[src++] & 0xFF, g = rgba[src++] & 0xFF, b = rgba[src++] & 0xFF, a = rgba[src++] & 0xFF;
                if (a != 0) {
                    opaque++;
                }
                argb[dst + x] = (a << 24) | (r << 16) | (g << 8) | b;
            }
        }
        return opaque;
    }
}
