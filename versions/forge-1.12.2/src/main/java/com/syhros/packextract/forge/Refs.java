package com.syhros.packextract.forge;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.OreIngredient;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.ItemIndex;
import com.syhros.packextract.util.Ids;

/**
 * Writes recipe ingredients and results as JSON references and records every item they mention.
 *
 * <pre>
 * {"item":"minecraft:log:0","amount":1}
 * {"item":"minecraft:log:*","amount":1,"anyOf":["minecraft:log:0",...]}      any damage value
 * {"ore":"plankWood","amount":1,"anyOf":[...]}                                ore dictionary
 * {"anyOf":[...],"amount":1}                                                  list of alternatives
 * {"fluid":"water","amount":1000}
 * optional: "chance":0.25 (0-1), "consumed":false
 * </pre>
 */
final class Refs {

    private final ItemIndex items;
    private final FluidIndex fluids;
    /** The ore dictionary's lists by identity: an OreIngredient holds the same list object. */
    private final Map<Object, String> oreLists = new IdentityHashMap<>();
    private Field oreField;
    /** Set while writing a GregTech chanced output: chance added per voltage tier above the recipe's (0-1). */
    double boost;

    Refs(ItemIndex items, FluidIndex fluids) {
        this.items = items;
        this.fluids = fluids;
        for (String name : OreDictionary.getOreNames()) {
            oreLists.put(OreDictionary.getOres(name, false), name);
        }
        try {
            oreField = OreIngredient.class.getDeclaredField("ores");
            oreField.setAccessible(true);
        } catch (Throwable ignored) {
            oreField = null;
        }
    }

    ItemIndex items() {
        return items;
    }

    void stack(JsonWriter w, ItemStack stack, double chance, boolean consumed) throws IOException {
        String id = Stacks.add(items, stack, "recipe");
        if (id == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("item").value(id);
        w.name("amount").value(stack.getCount());
        if (stack.getMetadata() == Ids.WILDCARD) {
            w.name("anyOf");
            w.beginArray();
            for (String v : items.variantsOf(Stacks.registryName(stack.getItem()))) {
                w.value(v);
            }
            w.endArray();
        }
        extras(w, chance, consumed);
        w.endObject();
    }

    /** An ingredient (one slot), with {@code amount} items. */
    void ingredient(JsonWriter w, Ingredient ingredient, int amount, double chance, boolean consumed)
        throws IOException {
        if (ingredient == null || ingredient == Ingredient.EMPTY) {
            w.nullValue();
            return;
        }
        ItemStack[] options = ingredient.getMatchingStacks();
        String ore = oreName(ingredient);
        if (options.length == 1 && ore == null) {
            ItemStack one = options[0].copy();
            one.setCount(amount);
            stack(w, one, chance, consumed);
            return;
        }
        list(w, Arrays.asList(options), ore, amount, chance, consumed);
    }

    /** A list of alternatives: an ore dictionary list or a mod's own. */
    void list(JsonWriter w, Collection<ItemStack> options, String ore, int amount, double chance, boolean consumed)
        throws IOException {
        w.beginObject();
        if (ore != null) {
            w.name("ore").value(ore);
        }
        w.name("amount").value(amount);
        w.name("anyOf");
        w.beginArray();
        for (ItemStack s : options) {
            String id = Stacks.add(items, s, "recipe");
            if (id == null) {
                continue;
            }
            if (s.getMetadata() == Ids.WILDCARD) {
                for (String v : items.variantsOf(Stacks.registryName(s.getItem()))) {
                    w.value(v);
                }
            } else {
                w.value(id);
            }
        }
        w.endArray();
        extras(w, chance, consumed);
        w.endObject();
    }

    void fluid(JsonWriter w, FluidStack stack, double chance, boolean consumed) throws IOException {
        if (stack == null || stack.getFluid() == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("fluid").value(Stacks.addFluid(fluids, stack.getFluid(), "recipe"));
        w.name("amount").value(stack.amount);
        if (stack.tag != null) {
            w.name("nbt").value(stack.tag.toString());
        }
        extras(w, chance, consumed);
        w.endObject();
    }

    /** The ore dictionary name of an ore ingredient, or null. */
    String oreName(Ingredient ingredient) {
        if (oreField == null || !(ingredient instanceof OreIngredient)) {
            return null;
        }
        try {
            return oreLists.get(oreField.get(ingredient));
        } catch (Throwable t) {
            return null;
        }
    }

    /** The ore dictionary name of a list, when it is one of the ore dictionary's own lists. */
    String oreName(Object list) {
        return oreLists.get(list);
    }

    private void extras(JsonWriter w, double chance, boolean consumed) throws IOException {
        if (boost > 0) {
            w.name("boost").value(Math.round(boost * 10000) / 10000.0);
        }
        if (!consumed) {
            w.name("consumed").value(false);
        }
        if (chance >= 0 && chance < 1) {
            w.name("chance").value(Math.round(chance * 10000) / 10000.0);
        }
    }
}
