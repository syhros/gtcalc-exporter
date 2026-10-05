package com.syhros.packextract.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * config/packextract.properties, used by the 1.12.2 and later versions (each Forge generation has a different
 * config API, a properties file works on all of them). 1.7.10 keeps its Forge config/packextract.cfg.
 *
 * <p>
 * Every setting can be overridden for scripted runs with a system property or environment variable:
 * {@code -Dpackextract.auto=true} or {@code PACKEXTRACT_AUTO=1}, and so on.
 */
public final class Config {

    public int imageSize = 64;
    public boolean images = true;
    public boolean viewerItems = true;
    public String outputDir = "pack-extract";
    public String packName = "";
    public int maxItems = 0;
    public boolean autoExport = false;
    public boolean quitAfterAutoExport = false;
    public int frameBudgetMillis = 40;

    private static final String TEMPLATE = "# Pack Extract settings\n"
        + "\n"
        + "# Width and height of every item and fluid image, in pixels (16-512).\n"
        + "imageSize=64\n"
        + "# Render an image of every item and fluid.\n"
        + "images=true\n"
        + "# Also take JEI's or NEI's item list (only available after joining a world).\n"
        + "viewerItems=true\n"
        + "# Where exports go. Relative paths are inside the instance (.minecraft) folder.\n"
        + "outputDir=pack-extract\n"
        + "# Name used for the export folder and manifest. Empty = the instance folder's name.\n"
        + "packName=\n"
        + "# Only list and draw the first N items and fluids, for quick tests (0 = everything).\n"
        + "maxItems=0\n"
        + "# Time spent exporting per frame in milliseconds. Higher is faster but the screen updates less often.\n"
        + "frameBudgetMillis=40\n"
        + "\n"
        + "# Export automatically when the game starts: opens (or creates) a flat creative world named\n"
        + "# packextract-auto, waits for it to load, exports, then returns to the menu.\n"
        + "autoExport=false\n"
        + "# Close the game when an automatic export finishes.\n"
        + "quitAfterAutoExport=false\n";

    public static Config load(File file) {
        Config c = new Config();
        Properties p = new Properties();
        if (file.isFile()) {
            try (InputStream in = new FileInputStream(file)) {
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // defaults
            }
        } else {
            try (Writer w = ExportJob.writer(file)) {
                w.write(TEMPLATE);
            } catch (IOException ignored) {
                // read-only folder: defaults
            }
        }
        c.imageSize = clamp(intValue(get(p, "imageSize", "PACKEXTRACT_IMAGE_SIZE"), 64), 16, 512);
        c.images = bool(get(p, "images", "PACKEXTRACT_IMAGES"), true);
        c.viewerItems = bool(get(p, "viewerItems", "PACKEXTRACT_VIEWER_ITEMS"), true);
        String out = get(p, "outputDir", "PACKEXTRACT_OUT");
        c.outputDir = out == null || out.isEmpty() ? "pack-extract" : out;
        String name = get(p, "packName", "PACKEXTRACT_PACK_NAME");
        c.packName = name == null ? "" : name;
        c.maxItems = Math.max(0, intValue(get(p, "maxItems", "PACKEXTRACT_MAX_ITEMS"), 0));
        c.frameBudgetMillis = clamp(intValue(get(p, "frameBudgetMillis", "PACKEXTRACT_FRAME_BUDGET"), 40), 5, 1000);
        c.autoExport = bool(get(p, "autoExport", "PACKEXTRACT_AUTO"), false);
        c.quitAfterAutoExport = bool(get(p, "quitAfterAutoExport", "PACKEXTRACT_QUIT"), false);
        return c;
    }

    /** System property packextract.&lt;key&gt;, then the environment variable, then the file. */
    private static String get(Properties p, String key, String env) {
        String v = System.getProperty("packextract." + key);
        if (v == null || v.isEmpty()) {
            v = System.getenv(env);
        }
        if (v == null || v.isEmpty()) {
            v = p.getProperty(key);
        }
        return v == null ? null : v.trim();
    }

    private static boolean bool(String v, boolean fallback) {
        if (v == null || v.isEmpty()) {
            return fallback;
        }
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes");
    }

    private static int intValue(String v, int fallback) {
        try {
            return v == null || v.isEmpty() ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** ".../GT_New_Horizons_2.8.4/.minecraft" gives "GT_New_Horizons_2.8.4". */
    public static String instanceName(File dataDir) {
        File dir = dataDir.getAbsoluteFile();
        if (dir.getName().equals(".")) {
            dir = dir.getParentFile();
        }
        String name = dir.getName();
        if ((name.equals(".minecraft") || name.equals("minecraft")) && dir.getParentFile() != null) {
            name = dir.getParentFile().getName();
        }
        return name.isEmpty() ? "pack" : name;
    }

    /** Export settings for a game folder. */
    public ExportJob.Settings settings(File gameDir, boolean withImages, String generator) {
        ExportJob.Settings s = new ExportJob.Settings();
        File out = new File(outputDir);
        s.outputRoot = out.isAbsolute() ? out : new File(gameDir, outputDir);
        s.packName = packName.isEmpty() ? instanceName(gameDir) : packName;
        s.generator = generator;
        s.images = withImages && images;
        s.imageSize = imageSize;
        s.viewerItems = viewerItems;
        s.maxItems = maxItems;
        return s;
    }
}
