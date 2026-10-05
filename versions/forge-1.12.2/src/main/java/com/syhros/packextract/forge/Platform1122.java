package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.IconRenderer;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.core.Platform;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.Problems;
import com.syhros.packextract.util.Ids;

/** Minecraft 1.12.2 with Forge: ore dictionary, crafting, furnace, JEI and GregTech CEu recipe maps. */
final class Platform1122 implements Platform {

    private final boolean viewerItems;
    private Refs refs;

    Platform1122(boolean viewerItems) {
        this.viewerItems = viewerItems;
    }

    @Override
    public String minecraftVersion() {
        return "1.12.2";
    }

    @Override
    public String loader() {
        return "Forge " + ForgeVersion.getVersion();
    }

    @Override
    public List<ModInfo> mods() {
        List<ModInfo> list = new ArrayList<>();
        for (ModContainer mod : Loader.instance().getActiveModList()) {
            list.add(new ModInfo(mod.getModId(), mod.getName(), mod.getVersion()));
        }
        return list;
    }

    @Override
    public void collectItems(ItemIndex items, Problems problems, Map<String, Number> counts) {
        int before = items.size();
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            NonNullList<ItemStack> subs = NonNullList.create();
            try {
                CreativeTabs[] tabs = item.getCreativeTabs();
                for (CreativeTabs tab : tabs) {
                    if (tab != null) {
                        item.getSubItems(tab, subs);
                    }
                }
            } catch (Throwable t) {
                problems.add("sub-items of " + Stacks.registryName(item), t);
            }
            if (subs.isEmpty()) {
                subs.add(new ItemStack(item, 1, 0));
            }
            for (ItemStack s : subs) {
                Stacks.add(items, s, "registry");
            }
        }
        counts.put("itemsFromRegistry", items.size() - before);

        before = items.size();
        if (viewerItems && Loader.isModLoaded("jei")) {
            try {
                for (ItemStack s : JeiItems.items()) {
                    Stacks.add(items, s, "jei");
                }
            } catch (Throwable t) {
                problems.add("JEI item list", t);
            }
        }
        counts.put("itemsAddedByJei", items.size() - before);

        before = items.size();
        for (String name : OreDictionary.getOreNames()) {
            for (ItemStack s : OreDictionary.getOres(name, false)) {
                Stacks.add(items, s, "oredict");
            }
        }
        counts.put("itemsAddedByOreDictionary", items.size() - before);
    }

    @Override
    public void collectFluids(FluidIndex fluids, ItemIndex items, Problems problems, Map<String, Number> counts) {
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            Stacks.addFluid(fluids, f, "registry");
        }
        if (viewerItems && Loader.isModLoaded("jei")) {
            try {
                for (FluidStack s : JeiItems.fluids()) {
                    Stacks.addFluid(fluids, s.getFluid(), "jei");
                }
            } catch (Throwable t) {
                problems.add("JEI fluid list", t);
            }
        }
        refs = new Refs(items, fluids);
    }

    @Override
    public List<RecipeSource> recipeSources(ItemIndex items, FluidIndex fluids, Problems problems) {
        List<RecipeSource> list = new ArrayList<>();
        list.add(Recipes.crafting(refs, problems));
        list.add(Recipes.smelting(refs, problems));
        if (GregTechRecipes.present()) {
            list.add(new GregTechRecipes(refs, problems));
        }
        return list;
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
                for (ItemStack s : OreDictionary.getOres(name, false)) {
                    String id = Stacks.add(items, s, "oredict");
                    if (id == null) {
                        continue;
                    }
                    if (s.getMetadata() == Ids.WILDCARD) {
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
        info.name = safe(stack::getDisplayName);
        info.unlocalizedName = safe(stack::getTranslationKey);
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
        FluidStack stack = new FluidStack(f, 1000);
        FluidInfo info = new FluidInfo();
        info.name = safe(() -> f.getLocalizedName(stack));
        info.unlocalizedName = safe(f::getUnlocalizedName);
        info.color = f.getColor(stack) & 0xFFFFFF;
        info.temperature = f.getTemperature();
        info.density = f.getDensity();
        info.viscosity = f.getViscosity();
        info.luminosity = f.getLuminosity();
        info.gaseous = f.isGaseous();
        ResourceLocation still = f.getStill(stack);
        info.texture = still != null ? still.toString() : null;
        Block block = f.getBlock();
        if (block != null && block.getRegistryName() != null) {
            info.block = block.getRegistryName().toString();
        }
        try {
            ItemStack bucket = FluidUtil.getFilledBucket(stack);
            if (!bucket.isEmpty()) {
                info.containers.add(new Container(Stacks.idOf(bucket), "minecraft:bucket:0", 1000));
            }
        } catch (Throwable ignored) {
            // no universal bucket for this fluid
        }
        return info;
    }

    @Override
    public IconRenderer renderer(int size, Problems problems) {
        return new LegacyIconRenderer(size);
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
