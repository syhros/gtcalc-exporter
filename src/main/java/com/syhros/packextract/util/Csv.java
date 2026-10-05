package com.syhros.packextract.util;

import java.io.IOException;
import java.io.Writer;

/** RFC 4180 CSV writing. */
public final class Csv {

    private final Writer out;

    public Csv(Writer out) {
        this.out = out;
    }

    public void row(Object... cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(escape(cells[i] == null ? "" : String.valueOf(cells[i])));
        }
        out.write("\r\n");
    }

    public static String escape(String s) {
        boolean quote = false;
        for (int i = 0; i < s.length() && !quote; i++) {
            char c = s.charAt(i);
            quote = c == ',' || c == '"' || c == '\n' || c == '\r';
        }
        if (!quote) {
            return s;
        }
        return '"' + s.replace("\"", "\"\"") + '"';
    }
}
