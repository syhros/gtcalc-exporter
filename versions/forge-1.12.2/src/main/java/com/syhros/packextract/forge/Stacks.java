package com.syhros.packextract.forge;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.util.Ids;

/** Item and fluid ids for Minecraft 1.12.2: {@code registryName:meta}, plus {@code #hash} of the NBT. */
final class Stacks {

    private Stacks() {}

    static String registryName(Item item) {
        ResourceLocation name = item.getRegistryName();
        return name != null ? name.toString() : "unregistered:" + item.getClass().getName();
    }

    static String nbt(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().toString() : null;
    }

    /** The id of a stack without adding it, or null for an empty stack. */
    static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return Ids.item(registryName(stack.getItem()), stack.getMetadata(), nbt(stack));
    }

    /**
     * Records a stack and returns its id. Wildcard stacks ("any damage value") are not recorded as items; their id ends
     * in {@code :*} and {@link ItemIndex#variantsOf} lists the matching items.
     */
    static String add(ItemIndex index, ItemStack stack, String source) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        String registryName = registryName(stack.getItem());
        int meta = stack.getMetadata();
        String nbt = nbt(stack);
        String id = Ids.item(registryName, meta, nbt);
        if (meta == Ids.WILDCARD) {
            return id;
        }
        return index.add(id, registryName, meta, nbt, () -> {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            return copy;
        }, source);
    }

    static String addFluid(FluidIndex index, Fluid fluid, String source) {
        return fluid == null ? null : index.add(fluid.getName(), fluid, source);
    }
}
