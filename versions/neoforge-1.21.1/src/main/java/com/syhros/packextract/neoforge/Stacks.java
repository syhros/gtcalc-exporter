package com.syhros.packextract.neoforge;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.util.Ids;

/**
 * Item and fluid ids on 1.21.1: the registry name, plus {@code #hash} of the stack's data components when they differ
 * from the item's defaults (what NBT was before 1.20.5).
 */
final class Stacks {

    private Stacks() {}

    static String registryName(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The stack's changed data components as text, or null when it has the item's defaults. */
    static String components(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return patch.isEmpty() ? null : patch.toString();
    }

    /** The id of a stack without adding it, or null for an empty stack. */
    static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return Ids.item(registryName(stack.getItem()), components(stack));
    }

    /** Records a stack and returns its id, or null for an empty stack. */
    static String add(ItemIndex index, ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String registryName = registryName(stack.getItem());
        String nbt = components(stack);
        return index.add(Ids.item(registryName, nbt), registryName, null, nbt, () -> stack.copyWithCount(1), source);
    }

    /** The still (source) form of a fluid: flowing water becomes water. */
    static Fluid source(Fluid fluid) {
        return fluid instanceof FlowingFluid ? ((FlowingFluid) fluid).getSource() : fluid;
    }

    static String fluidId(Fluid fluid) {
        return BuiltInRegistries.FLUID.getKey(source(fluid)).toString();
    }

    /** Records a fluid (its still form) and returns its id, or null for the empty fluid. */
    static String addFluid(FluidIndex index, Fluid fluid, String source) {
        if (fluid == null || fluid == Fluids.EMPTY) {
            return null;
        }
        Fluid still = source(fluid);
        return index.add(fluidId(still), still, source);
    }
}
