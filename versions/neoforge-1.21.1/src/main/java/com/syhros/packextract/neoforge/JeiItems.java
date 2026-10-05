package com.syhros.packextract.neoforge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IJeiRuntime;

/**
 * JEI's ingredient list, which includes variants that are in no creative tab. Only loaded when JEI is installed
 * (JEI finds this class by its annotation; the rest of the mod checks {@code ModList.isLoaded("jei")} first).
 */
@JeiPlugin
public class JeiItems implements IModPlugin {

    private static volatile IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(PackExtractMod.MODID, "items");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    static boolean ready() {
        return runtime != null;
    }

    static List<ItemStack> items() {
        IJeiRuntime r = runtime;
        if (r == null) {
            return new ArrayList<>();
        }
        Collection<ItemStack> all = r.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK);
        return new ArrayList<>(all);
    }

    static List<FluidStack> fluids() {
        IJeiRuntime r = runtime;
        if (r == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(r.getIngredientManager().getAllIngredients(NeoForgeTypes.FLUID_STACK));
    }
}
