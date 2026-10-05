package com.syhros.packextract.export;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.util.Ids;

/** Item and fluid ids for Minecraft 1.7.10: {@code registryName:meta}, plus {@code #hash} of the NBT. */
public final class Stacks {

    private Stacks() {}

    public static String registryName(Item item) {
        Object name = Item.itemRegistry.getNameForObject(item);
        return name != null ? name.toString() : "unregistered:" + item.getClass().getName();
    }

    /** The id of a stack without adding it, or null for an empty stack. */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : null;
        return Ids.item(registryName(stack.getItem()), stack.getItemDamage(), nbt);
    }

    /**
     * Records a stack and returns its id. Wildcard stacks ("any damage value") are not recorded as items; their id
     * ends in {@code :*} and {@link ItemIndex#variantsOf} lists the matching items.
     */
    public static String add(ItemIndex index, ItemStack stack, String source) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String registryName = registryName(stack.getItem());
        int meta = stack.getItemDamage();
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : null;
        String id = Ids.item(registryName, meta, nbt);
        if (meta == Ids.WILDCARD) {
            return id;
        }
        return index.add(id, registryName, meta, nbt, () -> {
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            return copy;
        }, source);
    }

    public static String addFluid(FluidIndex index, Fluid fluid, String source) {
        return index.add(fluid.getName(), fluid, source);
    }
}
