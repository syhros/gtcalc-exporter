package com.syhros.packextract.export;

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

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.PackExtract;
import com.syhros.packextract.Tags;
import com.syhros.packextract.util.Colors;
import com.syhros.packextract.util.Csv;
import com.syhros.packextract.util.Ids;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/**
 * One export run. {@link #step} is called every frame from the progress screen and does a slice of the work, so the
 * game keeps drawing and the progress stays visible.
 */
public final class ExportJob {

    public static final class Settings {

        public File outputRoot;
        public String packName;
        public boolean images = true;
        public int imageSize = 64;
        public boolean neiItems = true;
    }

    private enum Stage {
        ITEMS("Collecting items"),
        FLUIDS("Collecting fluids"),
        CRAFTING("Crafting recipes"),
        SMELTING("Furnace recipes"),
        GREGTECH("GregTech recipes"),
        OREDICT("Ore dictionary"),
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

    private final Settings settings;
    private final File dir;
    private final ItemIndex items = new ItemIndex();
    private final FluidIndex fluids = new FluidIndex();
    private final Problems problems = new Problems();
    private final Map<String, Number> counts = new LinkedHashMap<String, Number>();
    private final Map<String, Long> timings = new LinkedHashMap<String, Long>();
    private final long startedAt = System.currentTimeMillis();
    private Stage stage = Stage.ITEMS;
    private long stageStart = System.nanoTime();
    private Refs refs;

    private GregTechRecipes gregtech;
    private List<GregTechRecipes.RecipeMapInfo> gtMaps;
    private int gtNext;
    private JsonArrayFile gtOut;

    private List<ItemIndex.Entry> toRender;
    private List<FluidIndex.Entry> fluidsToRender;
    private int renderNext;
    private IconRenderer renderer;
    private PngWriter png;
    private final Ids.FileNames itemFiles = new Ids.FileNames();
    private final Ids.FileNames fluidFiles = new Ids.FileNames();
    private int blank;
    private String failure;

    public ExportJob(Settings settings) {
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
                return stage.label + " " + renderNext + " / " + (toRender == null ? 0 : toRender.size());
            case FLUID_IMAGES:
                return stage.label + " " + renderNext + " / " + (fluidsToRender == null ? 0 : fluidsToRender.size());
            case GREGTECH:
                return stage.label + " " + gtNext + " / " + (gtMaps == null ? "?" : gtMaps.size()) + " maps";
            default:
                return stage.label;
        }
    }

    /** 0-1 progress within the current stage, -1 when unknown. */
    public float stageProgress() {
        if (stage == Stage.ITEM_IMAGES && toRender != null && !toRender.isEmpty()) {
            return renderNext / (float) toRender.size();
        }
        if (stage == Stage.FLUID_IMAGES && fluidsToRender != null && !fluidsToRender.isEmpty()) {
            return renderNext / (float) fluidsToRender.size();
        }
        if (stage == Stage.GREGTECH && gtMaps != null && !gtMaps.isEmpty()) {
            return gtNext / (float) gtMaps.size();
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
                    collectItems();
                    next(Stage.FLUIDS);
                    break;
                case FLUIDS:
                    fluids.addRegistered();
                    for (FluidContainerRegistry.FluidContainerData d : FluidContainerRegistry
                        .getRegisteredFluidContainerData()) {
                        if (d.fluid != null && d.fluid.getFluid() != null) {
                            fluids.add(d.fluid.getFluid(), "fluid-container");
                        }
                    }
                    refs = new Refs(items, fluids);
                    next(Stage.CRAFTING);
                    break;
                case CRAFTING:
                    try (JsonArrayFile out = new JsonArrayFile(new File(dir, "recipes/crafting.json"))) {
                        int[] c = CraftingRecipes.writeCrafting(out, refs, problems);
                        counts.put("craftingRecipes", c[0]);
                        counts.put("craftingSpecialRecipes", c[1]);
                    }
                    next(Stage.SMELTING);
                    break;
                case SMELTING:
                    try (JsonArrayFile out = new JsonArrayFile(new File(dir, "recipes/smelting.json"))) {
                        counts.put("smeltingRecipes", CraftingRecipes.writeSmelting(out, refs, problems));
                    }
                    next(Stage.GREGTECH);
                    break;
                case GREGTECH:
                    stepGregTech(deadline);
                    break;
                case OREDICT:
                    writeOreDictionary();
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
                    PackExtract.LOG.info("Export finished: {} ({})", dir.getAbsolutePath(), counts);
                    break;
                default:
                    break;
            }
        } catch (Throwable t) {
            PackExtract.LOG.error("Export failed in stage " + stage, t);
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
        PackExtract.LOG.info("Export: {}", s.label);
    }

    private void collectItems() {
        int registry = ItemSources.fromRegistry(items, problems);
        int nei = settings.neiItems ? ItemSources.fromNei(items, problems) : 0;
        int ore = ItemSources.fromOreDictionary(items);
        int containers = ItemSources.fromFluidContainers(items);
        counts.put("itemsFromRegistry", registry);
        counts.put("itemsAddedByNei", nei);
        counts.put("itemsAddedByOreDictionary", ore);
        counts.put("itemsAddedByFluidContainers", containers);
    }

    private void stepGregTech(long deadline) throws IOException {
        if (gtMaps == null) {
            gtMaps = GregTechRecipes.maps(problems);
            gregtech = new GregTechRecipes();
            gtOut = new JsonArrayFile(new File(dir, "recipes/gregtech.json"));
        }
        while (gtNext < gtMaps.size()) {
            gregtech.writeMap(gtOut, refs, problems, gtMaps.get(gtNext++));
            if (System.nanoTime() > deadline) {
                return;
            }
        }
        gtOut.close();
        counts.put("gregtechRecipes", gtOut.count());
        counts.put("gregtechMaps", gtMaps.size());
        try (Writer w = writer(new File(dir, "recipes/gregtech-maps.json"))) {
            JsonWriter j = json(w);
            j.beginArray();
            for (GregTechRecipes.RecipeMapInfo m : gtMaps) {
                j.beginObject();
                j.name("id").value(m.id);
                j.name("name").value(m.name);
                j.name("recipes").value(m.written);
                j.endObject();
            }
            j.endArray();
            j.flush();
        }
        next(Stage.OREDICT);
    }

    private void writeOreDictionary() throws IOException {
        try (Writer w = writer(new File(dir, "oredict.json"))) {
            JsonWriter j = json(w);
            j.beginObject();
            String[] names = OreDictionary.getOreNames();
            java.util.Arrays.sort(names);
            for (String name : names) {
                j.name(name);
                j.beginArray();
                for (ItemStack s : OreDictionary.getOres(name)) {
                    String id = items.add(s, "oredict");
                    if (id == null) {
                        continue;
                    }
                    if (s.getItemDamage() == Ids.WILDCARD) {
                        for (String v : items.variantsOf(ItemIndex.registryName(s.getItem()))) {
                            j.value(v);
                        }
                    } else {
                        j.value(id);
                    }
                }
                j.endArray();
            }
            j.endObject();
            j.flush();
            counts.put("oreDictionaryNames", names.length);
        }
    }

    private void stepItemImages(long deadline) {
        if (toRender == null) {
            toRender = new ArrayList<ItemIndex.Entry>(items.all());
            renderer = new IconRenderer(settings.imageSize);
            png = new PngWriter(problems);
            if (!renderer.offscreen()) {
                problems.note("Framebuffers are off; icons are drawn on screen instead (turn on FBOs in video settings)");
            }
        }
        int size = renderer.size();
        while (renderNext < toRender.size()) {
            ItemIndex.Entry e = toRender.get(renderNext++);
            try {
                byte[] rgba = renderer.renderItem(e.stack);
                int[] argb = new int[size * size];
                if (Colors.rgbaToArgb(rgba, size, argb) == 0) {
                    e.imageBlank = true;
                    blank++;
                }
                String file = itemFiles.claim(e.id, ".png");
                e.image = "images/items/" + file;
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
        if (fluidsToRender == null) {
            fluidsToRender = new ArrayList<FluidIndex.Entry>(fluids.all());
        }
        int size = renderer.size();
        while (renderNext < fluidsToRender.size()) {
            FluidIndex.Entry e = fluidsToRender.get(renderNext++);
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
        try (Writer jw = writer(new File(dir, "items.json")); Writer cw = writer(new File(dir, "items.csv"))) {
            JsonWriter j = json(jw);
            Csv csv = new Csv(cw);
            csv.row("id", "registryName", "meta", "nbt", "name", "mod", "oreDict", "image");
            j.beginArray();
            for (ItemIndex.Entry e : items.all()) {
                String name = safe(() -> e.stack.getDisplayName());
                String unlocalized = safe(() -> e.stack.getUnlocalizedName());
                List<String> ores = new ArrayList<String>();
                try {
                    for (int id : OreDictionary.getOreIDs(e.stack)) {
                        ores.add(OreDictionary.getOreName(id));
                    }
                } catch (Throwable ignored) {
                    // some items throw from their equality checks
                }
                j.beginObject();
                j.name("id").value(e.id);
                j.name("registryName").value(e.registryName);
                j.name("mod").value(e.mod());
                j.name("meta").value(e.meta);
                if (e.nbt != null) {
                    j.name("nbt").value(e.nbt);
                }
                j.name("name").value(name);
                j.name("unlocalizedName").value(unlocalized);
                j.name("isBlock").value(e.stack.getItem() instanceof ItemBlock);
                j.name("oreDict");
                j.beginArray();
                for (String o : ores) {
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
                csv.row(e.id, e.registryName, e.meta, e.nbt, name, e.mod(), String.join(";", ores), e.image);
            }
            j.endArray();
            j.flush();
        }
    }

    private void writeFluids() throws IOException {
        Map<String, List<FluidContainerRegistry.FluidContainerData>> containers = new LinkedHashMap<String, List<FluidContainerRegistry.FluidContainerData>>();
        for (FluidContainerRegistry.FluidContainerData d : FluidContainerRegistry.getRegisteredFluidContainerData()) {
            if (d.fluid == null || d.fluid.getFluid() == null) {
                continue;
            }
            String id = d.fluid.getFluid().getName();
            List<FluidContainerRegistry.FluidContainerData> list = containers.get(id);
            if (list == null) {
                list = new ArrayList<FluidContainerRegistry.FluidContainerData>();
                containers.put(id, list);
            }
            list.add(d);
        }
        try (Writer jw = writer(new File(dir, "fluids.json")); Writer cw = writer(new File(dir, "fluids.csv"))) {
            JsonWriter j = json(jw);
            Csv csv = new Csv(cw);
            csv.row("id", "name", "color", "temperature", "gaseous", "image");
            j.beginArray();
            for (FluidIndex.Entry e : fluids.all()) {
                Fluid f = e.fluid;
                String name = safe(() -> new FluidStack(f, 1000).getLocalizedName());
                String color = Colors.hex(f.getColor());
                j.beginObject();
                j.name("id").value(e.id);
                j.name("name").value(name);
                j.name("unlocalizedName").value(safe(() -> f.getUnlocalizedName()));
                j.name("color").value(color);
                j.name("temperature").value(f.getTemperature());
                j.name("density").value(f.getDensity());
                j.name("viscosity").value(f.getViscosity());
                j.name("luminosity").value(f.getLuminosity());
                j.name("gaseous").value(f.isGaseous());
                String icon = safe(() -> f.getStillIcon() != null ? f.getStillIcon().getIconName() : null);
                if (icon != null) {
                    j.name("texture").value(icon);
                }
                Block block = f.getBlock();
                if (block != null) {
                    Object blockName = Block.blockRegistry.getNameForObject(block);
                    if (blockName != null) {
                        j.name("block").value(blockName.toString());
                    }
                }
                List<FluidContainerRegistry.FluidContainerData> list = containers.get(e.id);
                if (list != null) {
                    j.name("containers");
                    j.beginArray();
                    for (FluidContainerRegistry.FluidContainerData d : list) {
                        j.beginObject();
                        j.name("filled").value(ItemIndex.idOf(d.filledContainer));
                        j.name("empty").value(ItemIndex.idOf(d.emptyContainer));
                        j.name("amount").value(d.fluid.amount);
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
                csv.row(e.id, name, color, f.getTemperature(), f.isGaseous(), e.image);
            }
            j.endArray();
            j.flush();
        }
    }

    private void writeManifest() throws IOException {
        try (Writer w = writer(new File(dir, "manifest.json"))) {
            JsonWriter j = json(w);
            j.setIndent("  ");
            j.beginObject();
            j.name("format").value(1);
            j.name("generator").value("Pack Extract " + Tags.VERSION);
            j.name("minecraft").value("1.7.10");
            j.name("pack").value(settings.packName);
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            j.name("generatedAt").value(iso.format(new Date(startedAt)));
            j.name("seconds").value((System.currentTimeMillis() - startedAt) / 1000);
            j.name("imageSize").value(settings.images ? settings.imageSize : 0);
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
            j.name("oreDictionary").value("oredict.json");
            j.name("craftingRecipes").value("recipes/crafting.json");
            j.name("smeltingRecipes").value("recipes/smelting.json");
            j.name("gregtechRecipes").value("recipes/gregtech.json");
            j.name("gregtechMaps").value("recipes/gregtech-maps.json");
            j.name("errors").value("errors.log");
            j.endObject();
            j.name("mods");
            j.beginArray();
            for (ModContainer mod : Loader.instance().getActiveModList()) {
                j.beginObject();
                j.name("id").value(mod.getModId());
                j.name("name").value(mod.getName());
                j.name("version").value(mod.getVersion());
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
        if (gtOut != null && stage == Stage.FAILED) {
            try {
                gtOut.close();
            } catch (IOException ignored) {
                // already failing
            }
        }
    }

    private interface Getter {

        String get() throws Throwable;
    }

    private static String safe(Getter g) {
        try {
            return g.get();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Writer writer(File f) throws IOException {
        f.getParentFile().mkdirs();
        return new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8), 1 << 16);
    }

    private static JsonWriter json(Writer w) {
        return new JsonWriter(w);
    }
}
