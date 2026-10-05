package com.syhros.packextract.core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.syhros.packextract.export.Problems;

/**
 * What one Minecraft version provides to the export. {@link ExportJob} runs the steps and writes the files; the
 * platform reads the game: items, fluids, recipes, tags, names and images.
 */
public interface Platform {

    /** "1.20.1". */
    String minecraftVersion();

    /** "Forge 47.3.0". */
    String loader();

    /** Every loaded mod. */
    List<ModInfo> mods();

    /**
     * Fills the index from everything that lists items (registries, creative tabs, ore dictionary or tags, JEI/NEI).
     * Recipes add the items they use as they are written.
     */
    void collectItems(ItemIndex items, Problems problems, Map<String, Number> counts);

    void collectFluids(FluidIndex fluids, ItemIndex items, Problems problems, Map<String, Number> counts);

    /** Recipe files, written in order once items and fluids are collected. */
    List<RecipeSource> recipeSources(ItemIndex items, FluidIndex fluids, Problems problems);

    /**
     * Writes the ore dictionary (oredict.json) or tags (tags.json) and returns the file name. Adds the counts it
     * wants in the manifest.
     */
    String writeTags(File dir, ItemIndex items, FluidIndex fluids, Problems problems, Map<String, Number> counts)
        throws IOException;

    /** Name of the per-item list of ore dictionary names or tags in items.json: "oreDict" or "tags". */
    String itemTagField();

    ItemInfo describeItem(ItemIndex.Entry entry);

    FluidInfo describeFluid(FluidIndex.Entry entry);

    IconRenderer renderer(int size, Problems problems);

    final class ModInfo {

        public final String id;
        public final String name;
        public final String version;

        public ModInfo(String id, String name, String version) {
            this.id = id;
            this.name = name;
            this.version = version;
        }
    }

    final class ItemInfo {

        public String name;
        public String unlocalizedName;
        public boolean isBlock;
        public List<String> tags = new ArrayList<String>();
    }

    final class FluidInfo {

        public String name;
        public String unlocalizedName;
        /** 0xRRGGBB tint, or -1 when unknown. */
        public int color = -1;
        public int temperature;
        public int density;
        public int viscosity;
        public int luminosity;
        public boolean gaseous;
        public String texture;
        public String block;
        public List<Container> containers = new ArrayList<Container>();
    }

    /** A filled container (bucket, cell...) of a fluid. */
    final class Container {

        public final String filled;
        public final String empty;
        public final int amount;

        public Container(String filled, String empty, int amount) {
            this.filled = filled;
            this.empty = empty;
            this.amount = amount;
        }
    }
}
