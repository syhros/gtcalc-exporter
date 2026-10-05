package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.Ingredient;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.JsonArrayFile;
import com.syhros.packextract.export.Problems;

/**
 * GregTech CEu (and the older GregTech CE) recipe maps on 1.12.2, read by reflection so no GregTech jar is needed to
 * build. Same format as the 1.7.10 GregTech 5 export, one object per recipe:
 *
 * <pre>
 * {"map":"macerator","index":0,"inputs":[...],"outputs":[...],"fluidInputs":[...],"fluidOutputs":[...],
 *  "eut":2,"duration":400,"hidden":false,"properties":{"temperature":1800}}
 * </pre>
 *
 * Chanced outputs carry {@code "chance"} (0-1, at the recipe's base tier) and {@code "boost"} (chance added per tier
 * above it, 0-1); non-consumed inputs carry {@code "consumed":false}.
 */
final class GregTechRecipes implements RecipeSource {

    private final Refs refs;
    private final Problems problems;
    private final List<Object> maps = new ArrayList<>();
    private final List<int[]> written = new ArrayList<>();

    GregTechRecipes(Refs refs, Problems problems) {
        this.refs = refs;
        this.problems = problems;
    }

    /** True when GregTech is installed. */
    static boolean present() {
        try {
            Class.forName("gregtech.api.recipes.RecipeMap");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public String file() {
        return "recipes/gregtech.json";
    }

    @Override
    public String manifestKey() {
        return "gregtechRecipes";
    }

    @Override
    public String label() {
        return "GregTech recipes";
    }

    @Override
    public int parts() {
        try {
            Class<?> rm = Class.forName("gregtech.api.recipes.RecipeMap");
            Collection<?> all = (Collection<?>) rm.getMethod("getRecipeMaps").invoke(null);
            maps.addAll(all);
        } catch (Throwable t) {
            problems.add("GregTech recipe maps", t);
        }
        return maps.size();
    }

    @Override
    public void writePart(int index, JsonArrayFile out) throws IOException {
        Object map = maps.get(index);
        String name = mapName(map);
        int before = out.count();
        Collection<?> recipes;
        try {
            recipes = (Collection<?>) call(map, "getRecipeList");
        } catch (Throwable t) {
            problems.add("recipes of map " + name, t);
            written.add(new int[] { 0 });
            return;
        }
        int i = 0;
        for (Object r : recipes) {
            final int n = i++;
            out.add(w -> write(w, name, n, r), problems, "GregTech recipe " + name + "#" + n);
        }
        written.add(new int[] { out.count() - before });
    }

    @Override
    public void finish(File dir, JsonArrayFile out, Map<String, Number> counts, Map<String, String> files)
        throws IOException {
        counts.put("gregtechRecipes", out.count());
        counts.put("gregtechMaps", maps.size());
        try (Writer w = ExportJob.writer(new File(dir, "recipes/gregtech-maps.json"))) {
            JsonWriter j = new JsonWriter(w);
            j.beginArray();
            for (int k = 0; k < maps.size(); k++) {
                Object map = maps.get(k);
                j.beginObject();
                j.name("id").value(mapName(map));
                String localized;
                try {
                    localized = String.valueOf(call(map, "getLocalizedName"));
                } catch (Throwable t) {
                    localized = mapName(map);
                }
                j.name("name").value(localized);
                j.name("recipes").value(k < written.size() ? written.get(k)[0] : 0);
                j.endObject();
            }
            j.endArray();
            j.flush();
        }
        files.put("gregtechMaps", "recipes/gregtech-maps.json");
    }

    private void write(JsonWriter w, String map, int index, Object r) throws Exception {
        w.beginObject();
        w.name("map").value(map);
        w.name("index").value(index);
        w.name("inputs");
        w.beginArray();
        for (Object in : list(call(r, "getInputs"))) {
            writeItemInput(w, in);
        }
        w.endArray();
        w.name("outputs");
        w.beginArray();
        for (Object o : list(call(r, "getOutputs"))) {
            refs.stack(w, (ItemStack) o, 1, true);
        }
        for (Object c : chanced(call(r, "getChancedOutputs"))) {
            Object stack = first(c, "getIngredient", "getItemStack");
            if (stack instanceof ItemStack) {
                writeChanced(w, (ItemStack) stack, null, c);
            }
        }
        w.endArray();
        w.name("fluidInputs");
        w.beginArray();
        for (Object in : list(call(r, "getFluidInputs"))) {
            writeFluidInput(w, in);
        }
        w.endArray();
        w.name("fluidOutputs");
        w.beginArray();
        for (Object o : list(call(r, "getFluidOutputs"))) {
            refs.fluid(w, (FluidStack) o, 1, true);
        }
        Object chancedFluids = optional(r, "getChancedFluidOutputs");
        for (Object c : chanced(chancedFluids)) {
            Object stack = first(c, "getIngredient", "getFluidStack");
            if (stack instanceof FluidStack) {
                writeChanced(w, null, (FluidStack) stack, c);
            }
        }
        w.endArray();
        w.name("eut").value(((Number) call(r, "getEUt")).longValue());
        w.name("duration").value(((Number) call(r, "getDuration")).intValue());
        Object hidden = optional(r, "isHidden");
        if (hidden instanceof Boolean) {
            w.name("hidden").value((Boolean) hidden);
        }
        writeProperties(w, r);
        w.endObject();
    }

    /** GTRecipeInput (2.x) or CountableIngredient (older). */
    private void writeItemInput(JsonWriter w, Object in) throws Exception {
        Object stacks = optional(in, "getInputStacks");
        if (stacks instanceof ItemStack[]) {
            int amount = ((Number) call(in, "getAmount")).intValue();
            boolean consumed = !Boolean.TRUE.equals(optional(in, "isNonConsumable"));
            ItemStack[] options = (ItemStack[]) stacks;
            String ore = null;
            if (Boolean.TRUE.equals(optional(in, "isOreDict"))) {
                Object id = optional(in, "getOreDict");
                if (id instanceof Integer) {
                    ore = net.minecraftforge.oredict.OreDictionary.getOreName((Integer) id);
                }
            }
            if (options.length == 1 && ore == null) {
                ItemStack one = options[0].copy();
                one.setCount(amount);
                refs.stack(w, one, 1, consumed);
            } else {
                refs.list(w, Arrays.asList(options), ore, amount, 1, consumed);
            }
            return;
        }
        Object ingredient = optional(in, "getIngredient");
        if (ingredient instanceof Ingredient) {
            int count = ((Number) call(in, "getCount")).intValue();
            refs.ingredient(w, (Ingredient) ingredient, Math.max(1, count), 1, count != 0);
            return;
        }
        w.nullValue();
    }

    /** GTRecipeInput (2.x) or FluidStack (older). */
    private void writeFluidInput(JsonWriter w, Object in) throws Exception {
        if (in instanceof FluidStack) {
            refs.fluid(w, (FluidStack) in, 1, true);
            return;
        }
        Object stack = optional(in, "getInputFluidStack");
        if (stack instanceof FluidStack) {
            FluidStack s = ((FluidStack) stack).copy();
            Object amount = optional(in, "getAmount");
            if (amount instanceof Number) {
                s.amount = ((Number) amount).intValue();
            }
            refs.fluid(w, s, 1, !Boolean.TRUE.equals(optional(in, "isNonConsumable")));
            return;
        }
        w.nullValue();
    }

    private void writeChanced(JsonWriter w, ItemStack item, FluidStack fluid, Object entry) throws Exception {
        int max = maxChance();
        double chance = Math.min(0.9999, ((Number) call(entry, "getChance")).intValue() / (double) max);
        Object boost = first(entry, "getChanceBoost", "getBoostPerTier");
        refs.boost = boost instanceof Number ? ((Number) boost).intValue() / (double) max : 0;
        try {
            if (item != null) {
                refs.stack(w, item, chance, true);
            } else {
                refs.fluid(w, fluid, chance, true);
            }
        } finally {
            refs.boost = 0;
        }
    }

    private static int maxChance() {
        try {
            return ((Number) Class.forName("gregtech.api.recipes.Recipe").getMethod("getMaxChancedValue").invoke(null))
                .intValue();
        } catch (Throwable t) {
            return 10000;
        }
    }

    /** Recipe properties (coil temperature, fusion EU to start...) by key. */
    private void writeProperties(JsonWriter w, Object r) throws Exception {
        Object storage = optional(r, "propertyStorage");
        if (storage == null) {
            storage = optional(r, "getRecipePropertyStorage");
        }
        if (storage == null) {
            return;
        }
        Object entries = optional(storage, "entrySet");
        if (entries == null) {
            entries = optional(storage, "getRecipeProperties");
        }
        if (!(entries instanceof Collection) || ((Collection<?>) entries).isEmpty()) {
            return;
        }
        w.name("properties");
        w.beginObject();
        for (Object o : (Collection<?>) entries) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            Object key = optional(e.getKey(), "getKey");
            w.name(key != null ? key.toString() : String.valueOf(e.getKey()));
            Object v = e.getValue();
            if (v instanceof Number) {
                w.value((Number) v);
            } else if (v instanceof Boolean) {
                w.value((Boolean) v);
            } else {
                w.value(String.valueOf(v));
            }
        }
        w.endObject();
    }

    private static String mapName(Object map) {
        try {
            Object n = map.getClass().getField("unlocalizedName").get(map);
            if (n != null) {
                return n.toString();
            }
        } catch (Throwable ignored) {
            // try the getter
        }
        try {
            return String.valueOf(call(map, "getUnlocalizedName"));
        } catch (Throwable t) {
            return String.valueOf(map);
        }
    }

    /** Entries of a ChancedOutputList (2.x) or a plain list of ChanceEntry (older). */
    private static List<?> chanced(Object o) throws Exception {
        if (o == null) {
            return new ArrayList<>();
        }
        if (o instanceof List) {
            return (List<?>) o;
        }
        Object entries = optional(o, "getChancedEntries");
        return entries instanceof List ? (List<?>) entries : new ArrayList<>();
    }

    private static List<?> list(Object o) {
        return o instanceof List ? (List<?>) o : new ArrayList<>();
    }

    private static Object call(Object o, String name) throws Exception {
        Method m = o.getClass().getMethod(name);
        return m.invoke(o);
    }

    private static Object optional(Object o, String name) {
        try {
            return call(o, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object first(Object o, String... names) {
        for (String n : names) {
            Object v = optional(o, n);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
