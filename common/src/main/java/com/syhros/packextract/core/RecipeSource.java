package com.syhros.packextract.core;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import com.syhros.packextract.export.JsonArrayFile;

/**
 * One recipe file (recipes/crafting.json, recipes/gregtech.json...). Written in parts (one GregTech recipe map, one
 * recipe type) so the progress screen keeps updating.
 */
public interface RecipeSource {

    /** Path inside the export folder, e.g. "recipes/crafting.json". */
    String file();

    /** Key for the file in the manifest's "files", e.g. "craftingRecipes". */
    String manifestKey();

    /** Shown on the progress screen. */
    String label();

    /** Number of parts; {@link #writePart} is called for 0 to parts()-1. Called once, before the first part. */
    int parts();

    void writePart(int index, JsonArrayFile out) throws IOException;

    /** After the last part: add counts and write any side files (adding them to {@code files}). */
    void finish(File dir, JsonArrayFile out, Map<String, Number> counts, Map<String, String> files)
        throws IOException;
}
