package com.syhros.packextract.client;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** config/packextract.cfg */
public final class ClientConfig {

    public static int imageSize = 64;
    public static boolean images = true;
    public static boolean neiItems = true;
    public static String outputDir = "pack-extract";
    public static String packName = "";
    public static boolean autoExport = false;
    public static boolean quitAfterAutoExport = false;
    public static int frameBudgetMillis = 40;

    private ClientConfig() {}

    public static void load(File file) {
        Configuration c = new Configuration(file);
        String g = "export";
        imageSize = c.getInt(
            "imageSize",
            g,
            64,
            16,
            512,
            "Width and height of every item and fluid image, in pixels. 32 is the in-game size at GUI scale 2.");
        images = c.getBoolean("images", g, true, "Render an image of every item and fluid.");
        neiItems = c.getBoolean(
            "neiItems",
            g,
            true,
            "Also take NotEnoughItems' item list (only available after joining a world).");
        outputDir = c.getString(
            "outputDir",
            g,
            "pack-extract",
            "Where exports go. Relative paths are inside the instance (.minecraft) folder.");
        packName = c.getString(
            "packName",
            g,
            "",
            "Name used for the export folder and manifest. Empty = the instance folder's name.");
        frameBudgetMillis = c.getInt(
            "frameBudgetMillis",
            g,
            40,
            5,
            1000,
            "Time spent exporting per frame. Higher is faster but the progress screen updates less often.");
        String a = "automation";
        autoExport = c.getBoolean(
            "autoExport",
            a,
            false,
            "Export automatically when the game starts: opens (or creates) a flat creative world named "
                + "packextract-auto, waits for NEI, exports, then returns to the menu.");
        quitAfterAutoExport = c.getBoolean(
            "quitAfterAutoExport",
            a,
            false,
            "Close the game when an automatic export finishes.");
        if (c.hasChanged()) {
            c.save();
        }
        // Overrides for scripted runs: -Dpackextract.auto=true / PACKEXTRACT_AUTO=1 and so on.
        autoExport = flag("packextract.auto", "PACKEXTRACT_AUTO", autoExport);
        quitAfterAutoExport = flag("packextract.quit", "PACKEXTRACT_QUIT", quitAfterAutoExport);
        String out = setting("packextract.out", "PACKEXTRACT_OUT");
        if (out != null) {
            outputDir = out;
        }
        String size = setting("packextract.imageSize", "PACKEXTRACT_IMAGE_SIZE");
        if (size != null) {
            imageSize = Math.max(16, Math.min(512, Integer.parseInt(size.trim())));
        }
    }

    private static String setting(String property, String env) {
        String v = System.getProperty(property);
        if (v == null || v.isEmpty()) {
            v = System.getenv(env);
        }
        return v == null || v.isEmpty() ? null : v;
    }

    private static boolean flag(String property, String env, boolean fallback) {
        String v = setting(property, env);
        if (v == null) {
            return fallback;
        }
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes");
    }
}
