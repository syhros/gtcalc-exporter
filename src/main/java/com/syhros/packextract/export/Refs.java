package com.syhros.packextract.export;

import java.io.IOException;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.stream.JsonWriter;
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
public final class Refs {

    private final ItemIndex items;
    private final FluidIndex fluids;
    private final Map<Object, String> oreLists = new IdentityHashMap<Object, String>();

    public Refs(ItemIndex items, FluidIndex fluids) {
        this.items = items;
        this.fluids = fluids;
        for (String name : OreDictionary.getOreNames()) {
            oreLists.put(OreDictionary.getOres(name), name);
        }
    }

    public ItemIndex items() {
        return items;
    }

    /** An ItemStack, a list of alternatives (ore dictionary or not), a FluidStack, or null. */
    public void write(JsonWriter w, Object ingredient, double chance, String source) throws IOException {
        if (ingredient instanceof ItemStack) {
            writeStack(w, (ItemStack) ingredient, chance, source);
        } else if (ingredient instanceof FluidStack) {
            writeFluid(w, (FluidStack) ingredient, chance);
        } else if (ingredient instanceof ItemStack[]) {
            writeList(w, java.util.Arrays.asList((ItemStack[]) ingredient), null, 1, chance, source);
        } else if (ingredient instanceof List) {
            List<?> list = (List<?>) ingredient;
            int amount = 1;
            if (!list.isEmpty() && list.get(0) instanceof ItemStack) {
                amount = Math.max(1, ((ItemStack) list.get(0)).stackSize);
            }
            writeList(w, list, oreLists.get(ingredient), amount, chance, source);
        } else if (ingredient instanceof String) {
            List<ItemStack> ores = OreDictionary.getOres((String) ingredient);
            writeList(w, ores, (String) ingredient, 1, chance, source);
        } else {
            w.nullValue();
        }
    }

    public void writeStack(JsonWriter w, ItemStack stack, double chance, String source) throws IOException {
        String id = items.add(stack, source);
        if (id == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("item").value(id);
        w.name("amount").value(stack.stackSize);
        if (stack.stackSize == 0) {
            w.name("consumed").value(false);
        }
        if (stack.getItemDamage() == Ids.WILDCARD) {
            w.name("anyOf");
            w.beginArray();
            for (String v : items.variantsOf(ItemIndex.registryName(stack.getItem()))) {
                w.value(v);
            }
            w.endArray();
        }
        writeChance(w, chance);
        w.endObject();
    }

    public void writeFluid(JsonWriter w, FluidStack stack, double chance) throws IOException {
        if (stack == null || stack.getFluid() == null) {
            w.nullValue();
            return;
        }
        w.beginObject();
        w.name("fluid").value(fluids.add(stack.getFluid(), "recipe"));
        w.name("amount").value(stack.amount);
        if (stack.tag != null) {
            w.name("nbt").value(stack.tag.toString());
        }
        writeChance(w, chance);
        w.endObject();
    }

    private void writeList(JsonWriter w, Collection<?> list, String oreName, int amount, double chance,
        String source) throws IOException {
        w.beginObject();
        if (oreName != null) {
            w.name("ore").value(oreName);
        }
        w.name("amount").value(amount);
        w.name("anyOf");
        w.beginArray();
        for (Object o : list) {
            if (!(o instanceof ItemStack)) {
                continue;
            }
            ItemStack s = (ItemStack) o;
            String id = items.add(s, source);
            if (id == null) {
                continue;
            }
            if (s.getItemDamage() == Ids.WILDCARD) {
                for (String v : items.variantsOf(ItemIndex.registryName(s.getItem()))) {
                    w.value(v);
                }
            } else {
                w.value(id);
            }
        }
        w.endArray();
        writeChance(w, chance);
        w.endObject();
    }

    private static void writeChance(JsonWriter w, double chance) throws IOException {
        if (chance >= 0 && chance < 1) {
            w.name("chance").value(Math.round(chance * 10000) / 10000.0);
        }
    }
}
