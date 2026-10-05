package com.syhros.packextract.forge;

import java.io.IOException;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonElement;
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
 * {"fluidTag":"forge:water","amount":1000,"anyOf":["minecraft:water"]}       fluid tag
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
        String tag = tagOf(ingredient, "tag");
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
        if (stack.hasTag()) {
            w.name("nbt").value(stack.getTag().toString());
        }
        extras(w, chance, consumed);
        w.endObject();
    }

    /** A list of fluid alternatives (a fluid tag or a mod's fluid ingredient). */
    void fluids(JsonWriter w, FluidStack[] options, int amount, String tag, double chance, boolean consumed)
        throws IOException {
        if (options.length == 1 && tag == null) {
            fluid(w, new FluidStack(options[0], amount), chance, consumed);
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

    String fluidId(Fluid fluid) {
        return Stacks.addFluid(fluids, fluid, "recipe");
    }

    /** "minecraft:logs" when the ingredient is a single tag (read from its JSON form), else null. */
    static String tagOf(Object ingredient, String key) {
        try {
            JsonElement json = (JsonElement) ingredient.getClass().getMethod("toJson").invoke(ingredient);
            if (json != null && json.isJsonObject() && json.getAsJsonObject().has(key)) {
                return json.getAsJsonObject().get(key).getAsString();
            }
        } catch (Throwable ignored) {
            // not every modded ingredient can be written as JSON
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
