package com.syhros.packextract.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.export.JsonArrayFile;
import com.syhros.packextract.export.PngWriter;
import com.syhros.packextract.export.Problems;
import com.syhros.packextract.util.Colors;
import com.syhros.packextract.util.Csv;
import com.syhros.packextract.util.Ids;

/**
 * One export run, the same on every Minecraft version. {@link #step} is called every frame from the progress screen
 * and does a slice of the work, so the game keeps drawing and the progress stays visible.
 */
public final class ExportJob {

    private static final Logger LOG = LogManager.getLogger("packextract");

    public static final class Settings {

        public File outputRoot;
        public String packName;
        /** "Pack Extract 1.1.0". */
        public String generator = "Pack Extract";
        public boolean images = true;
        public int imageSize = 64;
        /** Also read NEI's or JEI's item list. */
        public boolean viewerItems = true;
        /**
         * Draw at most N item images and N fluid images (0 = all), for quick tests. Everything is still listed and
         * every recipe written; the drawn ones are the first half plus an even spread over the rest, so every mod
         * gets some.
         */
        public int maxItems;
    }

    private enum Stage {
        ITEMS("Collecting items"),
        FLUIDS("Collecting fluids"),
        RECIPES("Recipes"),
        TAGS("Ore dictionary and tags"),
        ITEM_IMAGES("Item images"),
        FLUID_IMAGES("Fluid images"),
        WRITE("Writing files"),
        DONE("Done"),
        FAILED("Failed");

        final String label;

        Stage(String label) {
            this.label = label;
        }
    }

    private final Platform platform;
    private final Settings settings;
    private final File dir;
    private final ItemIndex items = new ItemIndex();
    private final FluidIndex fluids = new FluidIndex();
    private final Problems problems = new Problems();
    private final Map<String, Number> counts = new LinkedHashMap<String, Number>();
    private final Map<String, Long> timings = new LinkedHashMap<String, Long>();
    private final Map<String, String> files = new LinkedHashMap<String, String>();
    private final long startedAt = System.currentTimeMillis();
    private Stage stage = Stage.ITEMS;
    private long stageStart = System.nanoTime();

    private List<RecipeSource> sources;
    private int sourceNext;
    private int partNext;
    private int partCount = -1;
    private JsonArrayFile recipeOut;
    private int recipeTotal;

    private List<ItemIndex.Entry> itemsToDraw;
    private List<FluidIndex.Entry> fluidsToDraw;
    private int renderNext;
    private IconRenderer renderer;
    private PngWriter png;
    private final Ids.FileNames itemFiles = new Ids.FileNames();
    private final Ids.FileNames fluidFiles = new Ids.FileNames();
    private int blank;
    private String failure;

    public ExportJob(Platform platform, Settings settings) {
        this.platform = platform;
        this.settings = settings;
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(new Date());
        this.dir = new File(settings.outputRoot, Ids.fileStem(settings.packName) + "-" + stamp);
    }

    public File dir() {
        return dir;
    }

    public boolean finished() {
        return stage == Stage.DONE || stage == Stage.FAILED;
    }

    public boolean failed() {
        return stage == Stage.FAILED;
    }

    public String failure() {
        return failure;
    }

    public Problems problems() {
        return problems;
    }

    public Map<String, Number> counts() {
        return counts;
    }

    public String status() {
        switch (stage) {
            case ITEM_IMAGES:
                return stage.label + " " + renderNext + " / " + (itemsToDraw == null ? 0 : itemsToDraw.size());
            case FLUID_IMAGES:
                return stage.label + " " + renderNext + " / " + (fluidsToDraw == null ? 0 : fluidsToDraw.size());
            case RECIPES:
                if (sources != null && sourceNext < sources.size()) {
                    RecipeSource s = sources.get(sourceNext);
                    return s.label() + (partCount > 1 ? " " + partNext + " / " + partCount : "");
                }
                return stage.label;
            default:
                return stage.label;
        }
    }

    /** 0-1 progress within the current stage, -1 when unknown. */
    public float stageProgress() {
        if (stage == Stage.ITEM_IMAGES && itemsToDraw != null && !itemsToDraw.isEmpty()) {
            return renderNext / (float) itemsToDraw.size();
        }
        if (stage == Stage.FLUID_IMAGES && fluidsToDraw != null && !fluidsToDraw.isEmpty()) {
            return renderNext / (float) fluidsToDraw.size();
        }
        if (stage == Stage.RECIPES && partCount > 1) {
            return partNext / (float) partCount;
        }
        return -1;
    }

    public String stageNumber() {
        return (Math.min(stage.ordinal(), Stage.DONE.ordinal()) + 1) + "/" + (Stage.DONE.ordinal() + 1);
    }

    /** Does up to {@code budgetMillis} of work. Must run on the render thread (images are drawn here). */
    public void step(long budgetMillis) {
        if (finished()) {
            return;
        }
        long deadline = System.nanoTime() + budgetMillis * 1_000_000L;
        try {
            switch (stage) {
                case ITEMS:
                    platform.collectItems(items, problems, counts);
                    next(Stage.FLUIDS);
                    break;
                case FLUIDS:
                    platform.collectFluids(fluids, items, problems, counts);
                    sources = platform.recipeSources(items, fluids, problems);
                    next(Stage.RECIPES);
                    break;
                case RECIPES:
                    stepRecipes(deadline);
                    break;
                case TAGS:
                    String tagFile = platform.writeTags(dir, items, fluids, problems, counts);
                    if (tagFile != null) {
                        files.put(tagFile.startsWith("oredict") ? "oreDictionary" : "tags", tagFile);
                    }
                    next(settings.images ? Stage.ITEM_IMAGES : Stage.WRITE);
                    break;
                case ITEM_IMAGES:
                    stepItemImages(deadline);
                    break;
                case FLUID_IMAGES:
                    stepFluidImages(deadline);
                    break;
                case WRITE:
                    writeAll();
                    next(Stage.DONE);
                    LOG.info("Export finished: {} ({})", dir.getAbsolutePath(), counts);
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            LOG.error("Export failed in stage " + stage, t);
            problems.add("stage " + stage, t);
            failure = stage.label + ": " + t;
            stage = Stage.FAILED;
            cleanup();
            try {
                writeErrors();
            } catch (IOException ignored) {
                // nothing more to do
            }
        }
    }

    public void cancel() {
        if (!finished()) {
            failure = "cancelled";
            stage = Stage.FAILED;
            cleanup();
        }
    }

    private void next(Stage s) {
        long now = System.nanoTime();
        timings.put(stage.name().toLowerCase(Locale.ROOT), (now - stageStart) / 1_000_000L);
        stageStart = now;
        stage = s;
        renderNext = 0;
        LOG.info("Export: {}", s.label);
    }

    private void stepRecipes(long deadline) throws IOException {
        while (sourceNext < sources.size()) {
            RecipeSource s = sources.get(sourceNext);
            if (recipeOut == null) {
                recipeOut = new JsonArrayFile(new File(dir, s.file()));
                partCount = s.parts();
                partNext = 0;
            }
            while (partNext < partCount) {
                s.writePart(partNext++, recipeOut);
                if (System.nanoTime() > deadline) {
                    return;
                }
            }
            recipeOut.close();
            recipeTotal += recipeOut.count();
            files.put(s.manifestKey(), s.file());
            s.finish(dir, recipeOut, counts, files);
            recipeOut = null;
            partCount = -1;
            sourceNext++;
            timings.put("recipes:" + s.manifestKey(), (System.nanoTime() - stageStart) / 1_000_000L);
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        counts.put("recipes", recipeTotal);
        next(Stage.TAGS);
    }

    /** All of the list, or {@code maxItems} of it: the first half, then an even spread over the rest. */
    private <T> List<T> sample(List<T> list) {
        int max = settings.maxItems;
        if (max <= 0 || list.size() <= max) {
            return list;
        }
        int head = max / 2;
        List<T> out = new ArrayList<T>(list.subList(0, head));
        int rest = max - head;
        double step = (list.size() - head) / (double) rest;
        for (int k = 0; k < rest; k++) {
            out.add(list.get(head + (int) (k * step)));
        }
        return out;
    }

    private void stepItemImages(long deadline) {
        if (itemsToDraw == null) {
            itemsToDraw = sample(new ArrayList<ItemIndex.Entry>(items.all()));
        }
        List<ItemIndex.Entry> list = itemsToDraw;
        if (renderer == null) {
            renderer = platform.renderer(settings.imageSize, problems);
            png = new PngWriter(problems);
            if (!renderer.offscreen()) {
                problems.note("Framebuffers are off; icons are drawn on screen instead (turn on FBOs in video settings)");
            }
        }
        int size = renderer.size();
        while (renderNext < list.size()) {
            ItemIndex.Entry e = list.get(renderNext++);
            try {
                byte[] rgba = renderer.renderItem(e.stack);
                int[] argb = new int[size * size];
                if (Colors.rgbaToArgb(rgba, size, argb) == 0) {
                    e.imageBlank = true;
                    blank++;
                }
                e.image = "images/items/" + itemFiles.claim(e.id, ".png");
                png.write(new File(dir, e.image), argb, size);
            } catch (Throwable t) {
                e.imageError = t.toString();
                problems.add("image of " + e.id, t);
            }
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        next(Stage.FLUID_IMAGES);
    }

    private void stepFluidImages(long deadline) {
        if (fluidsToDraw == null) {
            fluidsToDraw = sample(new ArrayList<FluidIndex.Entry>(fluids.all()));
        }
        List<FluidIndex.Entry> list = fluidsToDraw;
        int size = renderer.size();
        while (renderNext < list.size()) {
            FluidIndex.Entry e = list.get(renderNext++);
            try {
                byte[] rgba = renderer.renderFluid(e.fluid);
                if (rgba == null) {
                    e.imageError = "no texture";
                } else {
                    int[] argb = new int[size * size];
                    if (Colors.rgbaToArgb(rgba, size, argb) == 0) {
                        e.imageBlank = true;
                        blank++;
                    }
                    e.image = "images/fluids/" + fluidFiles.claim(e.id, ".png");
                    png.write(new File(dir, e.image), argb, size);
                }
            } catch (Throwable t) {
                e.imageError = t.toString();
                problems.add("image of fluid " + e.id, t);
            }
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        next(Stage.WRITE);
    }

    private void writeAll() throws IOException, InterruptedException {
        if (png != null) {
            png.finish();
            counts.put("imagesWritten", png.written());
            counts.put("blankImages", blank);
        }
        cleanup();
        writeItems();
        writeFluids();
        counts.put("items", items.size());
        counts.put("fluids", fluids.size());
        counts.put("errors", problems.errors());
        writeManifest();
        writeErrors();
    }

    private void writeItems() throws IOException {
        String tagField = platform.itemTagField();
        try (Writer jw = writer(new File(dir, "items.json")); Writer cw = writer(new File(dir, "items.csv"))) {
            JsonWriter j = new JsonWriter(jw);
            Csv csv = new Csv(cw);
            csv.row("id", "registryName", "meta", "nbt", "name", "mod", tagField, "image");
            j.beginArray();
            for (ItemIndex.Entry e : items.all()) {
                Platform.ItemInfo info;
                try {
                    info = platform.describeItem(e);
                } catch (Throwable t) {
                    problems.add("describing " + e.id, t);
                    info = new Platform.ItemInfo();
                }
                j.beginObject();
                j.name("id").value(e.id);
                j.name("registryName").value(e.registryName);
                j.name("mod").value(e.mod());
                if (e.meta != null) {
                    j.name("meta").value(e.meta);
                }
                if (e.nbt != null) {
                    j.name("nbt").value(e.nbt);
                }
                j.name("name").value(info.name);
                j.name("unlocalizedName").value(info.unlocalizedName);
                j.name("isBlock").value(info.isBlock);
                j.name(tagField);
                j.beginArray();
                for (String o : info.tags) {
                    j.value(o);
                }
                j.endArray();
                if (e.image != null) {
                    j.name("image").value(e.image);
                }
                if (e.imageBlank) {
                    j.name("imageBlank").value(true);
                }
                if (e.imageError != null) {
                    j.name("imageError").value(e.imageError);
                }
                j.name("sources");
                j.beginArray();
                for (String s : e.sources) {
                    j.value(s);
                }
                j.endArray();
                j.endObject();
                csv.row(e.id, e.registryName, e.meta, e.nbt, info.name, e.mod(), String.join(";", info.tags), e.image);
            }
            j.endArray();
            j.flush();
        }
    }

    private void writeFluids() throws IOException {
        try (Writer jw = writer(new File(dir, "fluids.json")); Writer cw = writer(new File(dir, "fluids.csv"))) {
            JsonWriter j = new JsonWriter(jw);
            Csv csv = new Csv(cw);
            csv.row("id", "name", "color", "temperature", "gaseous", "image");
            j.beginArray();
            for (FluidIndex.Entry e : fluids.all()) {
                Platform.FluidInfo f;
                try {
                    f = platform.describeFluid(e);
                } catch (Throwable t) {
                    problems.add("describing fluid " + e.id, t);
                    f = new Platform.FluidInfo();
                }
                String color = f.color < 0 ? null : Colors.hex(f.color);
                j.beginObject();
                j.name("id").value(e.id);
                j.name("name").value(f.name);
                j.name("unlocalizedName").value(f.unlocalizedName);
                if (color != null) {
                    j.name("color").value(color);
                }
                j.name("temperature").value(f.temperature);
                j.name("density").value(f.density);
                j.name("viscosity").value(f.viscosity);
                j.name("luminosity").value(f.luminosity);
                j.name("gaseous").value(f.gaseous);
                if (f.texture != null) {
                    j.name("texture").value(f.texture);
                }
                if (f.block != null) {
                    j.name("block").value(f.block);
                }
                if (!f.containers.isEmpty()) {
                    j.name("containers");
                    j.beginArray();
                    for (Platform.Container c : f.containers) {
                        j.beginObject();
                        j.name("filled").value(c.filled);
                        j.name("empty").value(c.empty);
                        j.name("amount").value(c.amount);
                        j.endObject();
                    }
                    j.endArray();
                }
                if (e.image != null) {
                    j.name("image").value(e.image);
                }
                if (e.imageBlank) {
                    j.name("imageBlank").value(true);
                }
                if (e.imageError != null) {
                    j.name("imageError").value(e.imageError);
                }
                j.endObject();
                csv.row(e.id, f.name, color, f.temperature, f.gaseous, e.image);
            }
            j.endArray();
            j.flush();
        }
    }

    private void writeManifest() throws IOException {
        try (Writer w = writer(new File(dir, "manifest.json"))) {
            JsonWriter j = new JsonWriter(w);
            j.setIndent("  ");
            j.beginObject();
            j.name("format").value(1);
            j.name("generator").value(settings.generator);
            j.name("minecraft").value(platform.minecraftVersion());
            j.name("loader").value(platform.loader());
            j.name("pack").value(settings.packName);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            j.name("generatedAt").value(iso.format(new Date(startedAt)));
            j.name("seconds").value((System.currentTimeMillis() - startedAt) / 1000);
            j.name("imageSize").value(settings.images ? settings.imageSize : 0);
            if (settings.maxItems > 0) {
                j.name("maxItems").value(settings.maxItems);
            }
            j.name("counts");
            j.beginObject();
            for (Map.Entry<String, Number> c : counts.entrySet()) {
                j.name(c.getKey()).value(c.getValue());
            }
            j.endObject();
            j.name("stageMillis");
            j.beginObject();
            for (Map.Entry<String, Long> t : timings.entrySet()) {
                j.name(t.getKey()).value(t.getValue());
            }
            j.endObject();
            j.name("files");
            j.beginObject();
            j.name("items").value("items.json");
            j.name("itemsCsv").value("items.csv");
            j.name("fluids").value("fluids.json");
            j.name("fluidsCsv").value("fluids.csv");
            for (Map.Entry<String, String> f : files.entrySet()) {
                j.name(f.getKey()).value(f.getValue());
            }
            j.name("errors").value("errors.log");
            j.endObject();
            j.name("mods");
            j.beginArray();
            for (Platform.ModInfo mod : platform.mods()) {
                j.beginObject();
                j.name("id").value(mod.id);
                j.name("name").value(mod.name);
                j.name("version").value(mod.version);
                j.endObject();
            }
            j.endArray();
            j.endObject();
            j.flush();
        }
    }

    private void writeErrors() throws IOException {
        try (Writer w = writer(new File(dir, "errors.log"))) {
            for (String line : problems.lines()) {
                w.write(line);
                w.write("\n");
            }
        }
    }

    private void cleanup() {
        if (renderer != null) {
            renderer.delete();
            renderer = null;
        }
        if (recipeOut != null && stage == Stage.FAILED) {
            try {
                recipeOut.close();
            } catch (IOException ignored) {
                // already failing
            }
            recipeOut = null;
        }
    }

    public static Writer writer(File f) throws IOException {
        f.getParentFile().mkdirs();
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16);
    }
}
