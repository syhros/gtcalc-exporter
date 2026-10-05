package com.syhros.packextract.forge;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.ingredients.IIngredientRegistry;
import mezz.jei.api.ingredients.VanillaTypes;

/**
 * JEI's ingredient list, which includes variants that are in no creative tab. Only loaded when JEI is installed
 * (JEI finds this class by its annotation; the rest of the mod checks {@code Loader.isModLoaded("jei")} first).
 */
@JEIPlugin
public class JeiItems implements IModPlugin {

    private static volatile IIngredientRegistry ingredients;

    @Override
    public void register(IModRegistry registry) {
        ingredients = registry.getIngredientRegistry();
    }

    static boolean ready() {
        return ingredients != null;
    }

    static List<ItemStack> items() {
        IIngredientRegistry r = ingredients;
        return r == null ? new ArrayList<>() : new ArrayList<>(r.getAllIngredients(VanillaTypes.ITEM));
    }

    static List<FluidStack> fluids() {
        IIngredientRegistry r = ingredients;
        return r == null ? new ArrayList<>() : new ArrayList<>(r.getAllIngredients(VanillaTypes.FLUID));
    }
}
