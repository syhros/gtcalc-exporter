package com.syhros.packextract.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;

class CsvAndColorsTest {

    @Test
    void csvQuotesOnlyWhenNeeded() throws Exception {
        StringWriter w = new StringWriter();
        new Csv(w).row("plain", "a,b", "say \"hi\"", null, 3);
        assertEquals("plain,\"a,b\",\"say \"\"hi\"\"\",,3\r\n", w.toString());
    }

    @Test
    void hexDropsAlpha() {
        assertEquals("#ff8000", Colors.hex(0x7FFF8000));
        assertEquals("#000001", Colors.hex(1));
    }

    @Test
    void readPixelsAreFlippedAndCounted() {
        // 2x2 image, bottom row first as glReadPixels returns it.
        byte[] rgba = { 1, 2, 3, (byte) 255, 0, 0, 0, 0, // bottom row
                10, 20, 30, (byte) 128, 0, 0, 0, 0 }; // top row
        int[] argb = new int[4];
        assertEquals(2, Colors.rgbaToArgb(rgba, 2, argb));
        assertArrayEquals(new int[] { 0x800A141E, 0, 0xFF010203, 0 }, argb);
    }
}
