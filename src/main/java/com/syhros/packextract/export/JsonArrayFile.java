package com.syhros.packextract.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import com.google.gson.stream.JsonWriter;

/**
 * A JSON array written one element at a time. Each element is built in memory first, so an element that throws
 * half-way is dropped instead of leaving broken JSON in the file.
 */
public final class JsonArrayFile implements AutoCloseable {

    public interface Element {

        void write(JsonWriter w) throws Exception;
    }

    private final Writer out;
    private boolean first = true;
    private int count;

    public JsonArrayFile(File file) throws IOException {
        file.getParentFile().mkdirs();
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8), 1 << 16);
        out.write("[\n");
    }

    /** Returns false (and writes nothing) when the element throws. */
    public boolean add(Element e, Problems problems, String what) throws IOException {
        StringWriter sw = new StringWriter();
        JsonWriter w = new JsonWriter(sw);
        try {
            e.write(w);
            w.flush();
        } catch (Throwable t) {
            problems.add(what, t);
            return false;
        }
        if (!first) {
            out.write(",\n");
        }
        first = false;
        out.write(sw.toString());
        count++;
        return true;
    }

    public int count() {
        return count;
    }

    @Override
    public void close() throws IOException {
        out.write("\n]\n");
        out.close();
    }
}
