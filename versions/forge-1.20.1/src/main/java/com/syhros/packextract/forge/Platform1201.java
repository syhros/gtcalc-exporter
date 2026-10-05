package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.versions.forge.ForgeVersion;

import com.google.gson.stream.JsonWriter;
import com.mojang.datafixers.util.Pair;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.IconRenderer;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.core.Platform;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.Problems;

/** Minecraft 1.20.1 with Forge: registries, creative tabs, JEI, tags, recipe manager, GregTech CEu Modern. */
final class Platform1201 implements Platform {

    private final boolean viewerItems;

    Platform1201(boolean viewerItems) {
        this.viewerItems = viewerItems;
    }

    @Override
    public String minecraftVersion() {
        return "1.20.1";
    }

    @Override
    public String loader() {
        return "Forge " + ForgeVersion.getVersion();
    }

    @Override
    public List<ModInfo> mods() {
        List<ModInfo> list = new ArrayList<>();
        for (IModInfo mod : ModList.get().getMods()) {
            list.add(new ModInfo(mod.getModId(), mod.getDisplayName(), mod.getVersion().toString()));
        }
        return list;
    }

    @Override
    public void collectItems(ItemIndex items, Problems problems, Map<String, Number> counts) {
        int before = items.size();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item != Items.AIR) {
                Stacks.add(items, new ItemStack(item), "registry");
            }
        }
        counts.put("itemsFromRegistry", items.size() - before);

        // Creative tabs list the variants (enchanted books, potions, filled cells...). They are built for the
        // world's enabled features, so this needs a world.
        before = items.size();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            try {
                CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
            } catch (Throwable t) {
                problems.add("building creative tabs", t);
            }
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                try {
                    for (ItemStack s : tab.getDisplayItems()) {
                        Stacks.add(items, s, "creative");
                    }
                } catch (Throwable t) {
                    problems.add("creative tab " + tab.getDisplayName().getString(), t);
                }
            }
        } else {
            problems.note("No world loaded: creative tab variants skipped (export from inside a world)");
        }
        counts.put("itemsAddedByCreativeTabs", items.size() - before);

        before = items.size();
        if (viewerItems && ModList.get().isLoaded("jei")) {
            try {
                for (ItemStack s : JeiItems.items()) {
                    Stacks.add(items, s, "jei");
                }
            } catch (Throwable t) {
                problems.add("JEI item list", t);
            }
        }
        counts.put("itemsAddedByJei", items.size() - before);
    }

    @Override
    public void collectFluids(FluidIndex fluids, ItemIndex items, Problems problems, Map<String, Number> counts) {
        for (Fluid f : BuiltInRegistries.FLUID) {
            if (f != Fluids.EMPTY && Stacks.source(f) == f) {
                Stacks.addFluid(fluids, f, "registry");
            }
        }
        if (viewerItems && ModList.get().isLoaded("jei")) {
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

    private Refs refs;

    @Override
    public List<RecipeSource> recipeSources(ItemIndex items, FluidIndex fluids, Problems problems) {
        return new Recipes(refs, problems).sources();
    }

    @Override
    public String writeTags(File dir, ItemIndex items, FluidIndex fluids, Problems problems,
        Map<String, Number> counts) throws IOException {
        try (Writer w = ExportJob.writer(new File(dir, "tags.json"))) {
            JsonWriter j = new JsonWriter(w);
            j.beginObject();
            j.name("items");
            counts.put("itemTags", writeTags(j, BuiltInRegistries.ITEM, item -> Stacks.idOf(new ItemStack(item))));
            j.name("fluids");
            counts.put("fluidTags", writeTags(j, BuiltInRegistries.FLUID, Stacks::fluidId));
            j.name("blocks");
            counts.put("blockTags", writeTags(j, BuiltInRegistries.BLOCK, block -> {
                ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
                return key.toString();
            }));
            j.endObject();
            j.flush();
        }
        return "tags.json";
    }

    private interface IdOf<T> {

        String id(T value);
    }

    private static <T> int writeTags(JsonWriter j, Registry<T> registry, IdOf<T> idOf) throws IOException {
        Map<String, Set<String>> sorted = new TreeMap<>();
        registry.getTags().forEach((Pair<TagKey<T>, HolderSet.Named<T>> pair) -> {
            Set<String> ids = new LinkedHashSet<>();
            for (Holder<T> h : pair.getSecond()) {
                try {
                    String id = idOf.id(h.value());
                    if (id != null) {
                        ids.add(id);
                    }
                } catch (Throwable ignored) {
                    // skip entries that cannot be named
                }
            }
            sorted.put(pair.getFirst().location().toString(), ids);
        });
        j.beginObject();
        for (Map.Entry<String, Set<String>> e : sorted.entrySet()) {
            j.name(e.getKey());
            j.beginArray();
            for (String id : e.getValue()) {
                j.value(id);
            }
            j.endArray();
        }
        j.endObject();
        return sorted.size();
    }

    @Override
    public String itemTagField() {
        return "tags";
    }

    @Override
    public ItemInfo describeItem(ItemIndex.Entry e) {
        ItemStack stack = (ItemStack) e.stack;
        ItemInfo info = new ItemInfo();
        info.name = stack.getHoverName().getString();
        info.unlocalizedName = stack.getDescriptionId();
        info.isBlock = stack.getItem() instanceof BlockItem;
        stack.getTags().forEach(t -> info.tags.add(t.location().toString()));
        return info;
    }

    @Override
    public FluidInfo describeFluid(FluidIndex.Entry e) {
        Fluid f = (Fluid) e.fluid;
        FluidType type = f.getFluidType();
        FluidInfo info = new FluidInfo();
        info.name = type.getDescription(new FluidStack(f, 1000)).getString();
        info.unlocalizedName = type.getDescriptionId();
        info.temperature = type.getTemperature();
        info.density = type.getDensity();
        info.viscosity = type.getViscosity();
        info.luminosity = type.getLightLevel();
        info.gaseous = type.isLighterThanAir();
        try {
            IClientFluidTypeExtensions client = IClientFluidTypeExtensions.of(f);
            info.color = client.getTintColor(new FluidStack(f, 1000)) & 0xFFFFFF;
            ResourceLocation still = client.getStillTexture(new FluidStack(f, 1000));
            info.texture = still != null ? still.toString() : null;
        } catch (Throwable ignored) {
            // no client extensions
        }
        Block block = f.defaultFluidState().createLegacyBlock().getBlock();
        if (block != Blocks.AIR) {
            info.block = BuiltInRegistries.BLOCK.getKey(block).toString();
        }
        Item bucket = f.getBucket();
        if (bucket != null && bucket != Items.AIR) {
            info.containers.add(new Container(Stacks.idOf(new ItemStack(bucket)), "minecraft:bucket", 1000));
        }
        return info;
    }

    @Override
    public IconRenderer renderer(int size, Problems problems) {
        return new ModernIconRenderer(size);
    }
}
