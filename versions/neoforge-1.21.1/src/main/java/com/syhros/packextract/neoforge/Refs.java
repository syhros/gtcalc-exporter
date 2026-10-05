package com.syhros.packextract.neoforge;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.fluids.FluidStack;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.FluidIndex;
import com.syhros.packextract.core.ItemIndex;

/**
 * Writes recipe ingredients and results as JSON references and records every item and fluid they mention.
 *
 * <pre>
 * {"item":"minecraft:oak_log","amount":1}
 * {"tag":"minecraft:logs","amount":1,"anyOf":["minecraft:oak_log",...]}       item tag
 * {"anyOf":[...],"amount":1}                                                  list of alternatives
 * {"fluid":"minecraft:water","amount":1000}
 * {"fluidTag":"c:water","amount":1000,"anyOf":["minecraft:water"]}           fluid tag
 * optional: "chance":0.25 (0-1), "consumed":false, "perTick":true
 * </pre>
 */
final class Refs {

    private final ItemIndex items;
    private final FluidIndex fluids;
    /** Set while writing GregTech inputs and outputs used every tick instead of once. */
    boolean perTick;

    Refs(ItemIndex items, FluidIndex fluids) {
        this.items = items;
        this.fluids = fluids;
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
        extras(w, chance, consumed);
        w.endObject();
    }

    /** A vanilla or modded ingredient; {@code amount} overrides 1 for sized ingredients. */
    void ingredient(JsonWriter w, Ingredient ingredient, int amount, double chance, boolean consumed)
        throws IOException {
        if (ingredient == null || ingredient.isEmpty()) {
            w.nullValue();
            return;
        }
        ItemStack[] options = ingredient.getItems();
        String tag = tagOf(ingredient);
        if (options.length == 1 && tag == null) {
            String id = Stacks.add(items, options[0], "recipe");
            w.beginObject();
            w.name("item").value(id);
            w.name("amount").value(amount);
            extras(w, chance, consumed);
            w.endObject();
            return;
        }
        w.beginObject();
        if (tag != null) {
            w.name("tag").value(tag);
        }
        w.name("amount").value(amount);
        w.name("anyOf");
        w.beginArray();
        for (ItemStack s : options) {
            String id = Stacks.add(items, s, "recipe");
            if (id != null) {
                w.value(id);
            }
        }
        w.endArray();
        extras(w, chance, consumed);
        w.endObject();
    }

    void fluid(JsonWriter w, FluidStack stack, double chance, boolean consumed) throws IOException {
        if (stack == null || stack.isEmpty()) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("fluid").value(Stacks.addFluid(fluids, stack.getFluid(), "recipe"));
        w.name("amount").value(stack.getAmount());
        if (!stack.getComponentsPatch().isEmpty()) {
            w.name("nbt").value(stack.getComponentsPatch().toString());
        }
        extras(w, chance, consumed);
        w.endObject();
    }

    /** A list of fluid alternatives (a fluid tag or a mod's fluid ingredient). */
    void fluids(JsonWriter w, FluidStack[] options, int amount, String tag, double chance, boolean consumed)
        throws IOException {
        if (options.length == 1 && tag == null) {
            fluid(w, options[0].copyWithAmount(amount), chance, consumed);
            return;
        }
        w.beginObject();
        if (tag != null) {
            w.name("fluidTag").value(tag);
        }
        w.name("amount").value(amount);
        w.name("anyOf");
        w.beginArray();
        for (FluidStack f : options) {
            String id = f == null ? null : Stacks.addFluid(fluids, f.getFluid(), "recipe");
            if (id != null) {
                w.value(id);
            }
        }
        w.endArray();
        extras(w, chance, consumed);
        w.endObject();
    }

    /**
     * "minecraft:logs" when the ingredient is a single tag, else null. 1.21 ingredients have no JSON form; their
     * values are read instead (a TagValue record holds the tag).
     */
    static String tagOf(Object ingredient) {
        try {
            Object[] values = null;
            Method getter = null;
            try {
                getter = ingredient.getClass().getMethod("getValues");
            } catch (NoSuchMethodException ignored) {
                // read the field instead
            }
            if (getter != null) {
                values = (Object[]) getter.invoke(ingredient);
            } else {
                Field f = Ingredient.class.getDeclaredField("values");
                f.setAccessible(true);
                values = (Object[]) f.get(ingredient);
            }
            if (values != null && values.length == 1) {
                Method tag = values[0].getClass().getMethod("tag");
                Object key = tag.invoke(values[0]);
                Method location = key.getClass().getMethod("location");
                return location.invoke(key).toString();
            }
        } catch (Throwable ignored) {
            // custom ingredients, or a single item
        }
        return null;
    }

    private void extras(JsonWriter w, double chance, boolean consumed) throws IOException {
        if (perTick) {
            w.name("perTick").value(true);
        }
        if (!consumed) {
            w.name("consumed").value(false);
        }
        if (chance >= 0 && chance < 1) {
            w.name("chance").value(Math.round(chance * 10000) / 10000.0);
        }
    }
}
