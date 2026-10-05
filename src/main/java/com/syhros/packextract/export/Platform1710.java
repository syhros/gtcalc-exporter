package com.syhros.packextract.export;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.IconRenderer;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.core.Platform;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.util.Ids;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

/** Minecraft 1.7.10 with Forge: ore dictionary, crafting, furnace and GregTech 5 recipe maps. */
public final class Platform1710 implements Platform {

    private final boolean neiItems;
    private Refs refs;
    private Map<String, List<Container>> containers;

    public Platform1710(boolean neiItems) {
        this.neiItems = neiItems;
    }

    @Override
    public String minecraftVersion() {
        return "1.7.10";
    }

    @Override
    public String loader() {
        return "Forge " + ForgeVersion.getVersion();
    }

    @Override
    public List<ModInfo> mods() {
        List<ModInfo> list = new ArrayList<ModInfo>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            list.add(new ModInfo(mod.getModId(), mod.getName(), mod.getVersion()));
        }
        return list;
    }

    @Override
    public void collectItems(ItemIndex items, Problems problems, Map<String, Number> counts) {
        counts.put("itemsFromRegistry", ItemSources.fromRegistry(items, problems));
        counts.put("itemsAddedByNei", neiItems ? ItemSources.fromNei(items, problems) : 0);
        counts.put("itemsAddedByOreDictionary", ItemSources.fromOreDictionary(items));
        counts.put("itemsAddedByFluidContainers", ItemSources.fromFluidContainers(items));
    }

    @Override
    public void collectFluids(FluidIndex fluids, ItemIndex items, Problems problems, Map<String, Number> counts) {
        for (Fluid f : net.minecraftforge.fluids.FluidRegistry.getRegisteredFluids().values()) {
            Stacks.addFluid(fluids, f, "registry");
        }
        for (FluidContainerRegistry.FluidContainerData d : FluidContainerRegistry.getRegisteredFluidContainerData()) {
            if (d.fluid != null && d.fluid.getFluid() != null) {
                Stacks.addFluid(fluids, d.fluid.getFluid(), "fluid-container");
            }
        }
        refs = new Refs(items, fluids);
    }

    @Override
    public List<RecipeSource> recipeSources(ItemIndex items, FluidIndex fluids, final Problems problems) {
        List<RecipeSource> list = new ArrayList<RecipeSource>();
        list.add(new Single("recipes/crafting.json", "craftingRecipes", "Crafting recipes") {

            private int[] c;

            @Override
            public void writePart(int index, JsonArrayFile out) throws IOException {
                c = CraftingRecipes.writeCrafting(out, refs, problems);
            }

            @Override
            void counts(Map<String, Number> counts, int written) {
                counts.put("craftingRecipes", c[0]);
                counts.put("craftingSpecialRecipes", c[1]);
            }
        });
        list.add(new Single("recipes/smelting.json", "smeltingRecipes", "Furnace recipes") {

            @Override
            public void writePart(int index, JsonArrayFile out) throws IOException {
                CraftingRecipes.writeSmelting(out, refs, problems);
            }

            @Override
            void counts(Map<String, Number> counts, int written) {
                counts.put("smeltingRecipes", written);
            }
        });
        list.add(new GregTechSource(problems));
        return list;
    }

    /** A recipe file written in one go. */
    private abstract static class Single implements RecipeSource {

        private final String file;
        private final String key;
        private final String label;

        Single(String file, String key, String label) {
            this.file = file;
            this.key = key;
            this.label = label;
        }

        @Override
        public String file() {
            return file;
        }

        @Override
        public String manifestKey() {
            return key;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public int parts() {
            return 1;
        }

        abstract void counts(Map<String, Number> counts, int written);

        @Override
        public void finish(File dir, JsonArrayFile out, Map<String, Number> counts,
            Map<String, String> files) {
            counts(counts, out.count());
        }
    }

    /** Every GregTech 5 recipe map, one part per map; also writes recipes/gregtech-maps.json. */
    private final class GregTechSource implements RecipeSource {

        private final Problems problems;
        private final GregTechRecipes gregtech = new GregTechRecipes();
        private List<GregTechRecipes.RecipeMapInfo> maps;

        GregTechSource(Problems problems) {
            this.problems = problems;
        }

        @Override
        public String file() {
            return "recipes/gregtech.json";
        }

        @Override
        public String manifestKey() {
            return "gregtechRecipes";
        }

        @Override
        public String label() {
            return "GregTech recipes";
        }

        @Override
        public int parts() {
            maps = GregTechRecipes.maps(problems);
            return maps.size();
        }

        @Override
        public void writePart(int index, JsonArrayFile out) throws IOException {
            gregtech.writeMap(out, refs, problems, maps.get(index));
        }

        @Override
        public void finish(File dir, JsonArrayFile out, Map<String, Number> counts,
            Map<String, String> files) throws IOException {
            counts.put("gregtechRecipes", out.count());
            counts.put("gregtechMaps", maps.size());
            try (Writer w = ExportJob.writer(new File(dir, "recipes/gregtech-maps.json"))) {
                JsonWriter j = new JsonWriter(w);
                j.beginArray();
                for (GregTechRecipes.RecipeMapInfo m : maps) {
                    j.beginObject();
                    j.name("id").value(m.id);
                    j.name("name").value(m.name);
                    j.name("recipes").value(m.written);
                    j.endObject();
                }
                j.endArray();
                j.flush();
            }
            files.put("gregtechMaps", "recipes/gregtech-maps.json");
        }
    }

    @Override
    public String writeTags(File dir, ItemIndex items, FluidIndex fluids, Problems problems,
        Map<String, Number> counts) throws IOException {
        try (Writer w = ExportJob.writer(new File(dir, "oredict.json"))) {
            JsonWriter j = new JsonWriter(w);
            j.beginObject();
            String[] names = OreDictionary.getOreNames();
            Arrays.sort(names);
            for (String name : names) {
                j.name(name);
                j.beginArray();
                for (ItemStack s : OreDictionary.getOres(name)) {
                    String id = Stacks.add(items, s, "oredict");
                    if (id == null) {
                        continue;
                    }
                    if (s.getItemDamage() == Ids.WILDCARD) {
                        for (String v : items.variantsOf(Stacks.registryName(s.getItem()))) {
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
        return "oredict.json";
    }

    @Override
    public String itemTagField() {
        return "oreDict";
    }

    @Override
    public ItemInfo describeItem(ItemIndex.Entry e) {
        ItemStack stack = (ItemStack) e.stack;
        ItemInfo info = new ItemInfo();
        info.name = safe(() -> stack.getDisplayName());
        info.unlocalizedName = safe(() -> stack.getUnlocalizedName());
        info.isBlock = stack.getItem() instanceof ItemBlock;
        try {
            for (int id : OreDictionary.getOreIDs(stack)) {
                info.tags.add(OreDictionary.getOreName(id));
            }
        } catch (Throwable ignored) {
            // some items throw from their equality checks
        }
        return info;
    }

    @Override
    public FluidInfo describeFluid(FluidIndex.Entry e) {
        Fluid f = (Fluid) e.fluid;
        FluidInfo info = new FluidInfo();
        info.name = safe(() -> new FluidStack(f, 1000).getLocalizedName());
        info.unlocalizedName = safe(() -> f.getUnlocalizedName());
        info.color = f.getColor() & 0xFFFFFF;
        info.temperature = f.getTemperature();
        info.density = f.getDensity();
        info.viscosity = f.getViscosity();
        info.luminosity = f.getLuminosity();
        info.gaseous = f.isGaseous();
        info.texture = safe(() -> f.getStillIcon() != null ? f.getStillIcon().getIconName() : null);
        Block block = f.getBlock();
        if (block != null) {
            Object blockName = Block.blockRegistry.getNameForObject(block);
            if (blockName != null) {
                info.block = blockName.toString();
            }
        }
        if (containers == null) {
            containers = new HashMap<String, List<Container>>();
            for (FluidContainerRegistry.FluidContainerData d : FluidContainerRegistry
                .getRegisteredFluidContainerData()) {
                if (d.fluid == null || d.fluid.getFluid() == null) {
                    continue;
                }
                String id = d.fluid.getFluid().getName();
                List<Container> list = containers.get(id);
                if (list == null) {
                    list = new ArrayList<Container>();
                    containers.put(id, list);
                }
                list.add(new Container(Stacks.idOf(d.filledContainer), Stacks.idOf(d.emptyContainer), d.fluid.amount));
            }
        }
        List<Container> list = containers.get(e.id);
        if (list != null) {
            info.containers.addAll(list);
        }
        return info;
    }

    @Override
    public IconRenderer renderer(int size, Problems problems) {
        return new GlIconRenderer(size);
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
}
