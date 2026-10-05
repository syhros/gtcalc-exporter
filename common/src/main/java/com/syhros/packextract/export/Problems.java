package com.syhros.packextract.export;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Errors and notes collected during an export; written to errors.log and counted in the manifest. */
public final class Problems {

    private static final Logger LOG = LogManager.getLogger("packextract");

    private final List<String> lines = new ArrayList<String>();
    private int errors;
    private int notes;

    public synchronized void add(String what, Throwable t) {
        errors++;
        if (errors <= 2000) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            String trace = sw.toString();
            if (trace.length() > 1500) {
                trace = trace.substring(0, 1500) + "...";
            }
            lines.add("ERROR " + what + ": " + trace);
        }
        if (errors <= 20) {
            LOG.warn("{} failed: {}", what, t.toString());
        }
    }

    public synchronized void note(String message) {
        notes++;
        lines.add("NOTE " + message);
        LOG.info(message);
    }

    public synchronized int errors() {
        return errors;
    }

    public synchronized int notes() {
        return notes;
    }

    public synchronized List<String> lines() {
        List<String> copy = new ArrayList<String>(lines);
        if (errors > 2000) {
            copy.add("... " + (errors - 2000) + " more errors not shown");
        }
        return copy;
    }
}
