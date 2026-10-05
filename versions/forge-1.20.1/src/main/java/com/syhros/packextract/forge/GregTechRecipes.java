package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.JsonElement;
import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.JsonArrayFile;
import com.syhros.packextract.export.Problems;

/**
 * GregTech CEu Modern recipes, read by reflection so the jar needs no GregTech at build time and works with and
 * without it installed. One object per recipe, close to the 1.7.10 GregTech 5 format:
 *
 * <pre>
 * {"map":"gtceu:macerator","id":"gtceu:macerator/macerate_iron_ore","inputs":[...],"outputs":[...],
 *  "fluidInputs":[...],"fluidOutputs":[...],"eut":2,"duration":400,"circuit":1,"temp":1800,
 *  "data":"{ebf_temp:1800}","conditions":["CleanroomCondition"],"other":[...]}
 * </pre>
 *
 * Chanced outputs carry {@code "chance"} (0-1); inputs with chance 0 (molds, lenses, circuits) carry
 * {@code "consumed":false}. Inputs and outputs used every tick instead of once carry {@code "perTick":true}.
 * Capabilities other than items, fluids and EU (computation, stress, mana...) go to "other".
 */
final class GregTechRecipes {

    private final Refs refs;
    private final Problems problems;
    private final Class<?> recipeClass;
    private final Map<String, Field> fields = new HashMap<>();

    private GregTechRecipes(Refs refs, Problems problems, Class<?> recipeClass) {
        this.refs = refs;
        this.problems = problems;
        this.recipeClass = recipeClass;
    }

    /** Null when GregTech CEu is not installed. */
    static GregTechRecipes create(Refs refs, Problems problems) {
        try {
            return new GregTechRecipes(refs, problems,
                Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipe"));
        } catch (ClassNotFoundException e) {
            return null;
        } catch (Throwable t) {
            problems.add("GregTech CEu recipe class", t);
            return null;
        }
    }

    /** True for a recipe type whose recipes are GregTech recipes. */
    boolean handles(List<Recipe<?>> recipes) {
        return recipes != null && !recipes.isEmpty() && recipeClass.isInstance(recipes.get(0));
    }

    RecipeSource source(Map<ResourceLocation, List<Recipe<?>>> byType) {
        List<ResourceLocation> types = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<Recipe<?>>> e : byType.entrySet()) {
            if (handles(e.getValue())) {
                types.add(e.getKey());
            }
        }
        return new RecipeSource() {

            private final Map<ResourceLocation, Integer> written = new LinkedHashMap<>();

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
                return types.size();
            }

            @Override
            public void writePart(int index, JsonArrayFile out) throws IOException {
                ResourceLocation type = types.get(index);
                int before = out.count();
                for (Recipe<?> r : byType.get(type)) {
                    out.add(w -> write(w, type.toString(), r), problems, "GregTech recipe " + r.getId());
                }
                written.put(type, out.count() - before);
            }

            @Override
            public void finish(File dir, JsonArrayFile out, Map<String, Number> counts, Map<String, String> files)
                throws IOException {
                counts.put("gregtechRecipes", out.count());
                counts.put("gregtechMaps", types.size());
                try (Writer w = ExportJob.writer(new File(dir, "recipes/gregtech-maps.json"))) {
                    JsonWriter j = new JsonWriter(w);
                    j.beginArray();
                    for (ResourceLocation type : types) {
                        j.beginObject();
                        j.name("id").value(type.toString());
                        j.name("name").value(I18n.get(type.toLanguageKey()));
                        j.name("recipes").value(written.getOrDefault(type, 0));
                        j.endObject();
                    }
                    j.endArray();
                    j.flush();
                }
                files.put("gregtechMaps", "recipes/gregtech-maps.json");
            }
        };
    }

    private void write(JsonWriter w, String map, Recipe<?> r) throws Exception {
        w.beginObject();
        w.name("map").value(map);
        w.name("id").value(r.getId().toString());
        Map<?, ?> inputs = (Map<?, ?>) get(r, "inputs");
        Map<?, ?> outputs = (Map<?, ?>) get(r, "outputs");
        Map<?, ?> tickInputs = (Map<?, ?>) get(r, "tickInputs");
        Map<?, ?> tickOutputs = (Map<?, ?>) get(r, "tickOutputs");
        List<String[]> other = new ArrayList<>();
        Integer[] circuit = new Integer[1];

        w.name("inputs");
        w.beginArray();
        writeContents(w, inputs, "item", true, false, circuit);
        writeContents(w, tickInputs, "item", true, true, circuit);
        w.endArray();
        w.name("outputs");
        w.beginArray();
        writeContents(w, outputs, "item", false, false, circuit);
        writeContents(w, tickOutputs, "item", false, true, circuit);
        w.endArray();
        w.name("fluidInputs");
        w.beginArray();
        writeContents(w, inputs, "fluid", true, false, circuit);
        writeContents(w, tickInputs, "fluid", true, true, circuit);
        w.endArray();
        w.name("fluidOutputs");
        w.beginArray();
        writeContents(w, outputs, "fluid", false, false, circuit);
        writeContents(w, tickOutputs, "fluid", false, true, circuit);
        w.endArray();

        long eut = energy(tickInputs) + energy(inputs);
        long eutOut = energy(tickOutputs) + energy(outputs);
        w.name("eut").value(eut);
        if (eutOut > 0) {
            w.name("eutOutput").value(eutOut);
        }
        Object duration = get(r, "duration");
        w.name("duration").value(duration instanceof Number ? ((Number) duration).intValue() : 0);
        if (circuit[0] != null) {
            w.name("circuit").value(circuit[0]);
        }
        Object data = get(r, "data");
        if (data instanceof CompoundTag && !((CompoundTag) data).isEmpty()) {
            CompoundTag tag = (CompoundTag) data;
            if (tag.contains("ebf_temp")) {
                w.name("temp").value(tag.getInt("ebf_temp"));
            }
            w.name("data").value(tag.toString());
        }
        Object conditions = get(r, "conditions");
        if (conditions instanceof Collection && !((Collection<?>) conditions).isEmpty()) {
            w.name("conditions");
            w.beginArray();
            for (Object c : (Collection<?>) conditions) {
                w.value(c.getClass().getSimpleName());
            }
            w.endArray();
        }
        collectOther(inputs, "in", false, other);
        collectOther(tickInputs, "in", true, other);
        collectOther(outputs, "out", false, other);
        collectOther(tickOutputs, "out", true, other);
        if (!other.isEmpty()) {
            w.name("other");
            w.beginArray();
            for (String[] o : other) {
                w.beginObject();
                w.name("capability").value(o[0]);
                w.name("io").value(o[1]);
                if (o[2] != null) {
                    w.name("perTick").value(true);
                }
                w.name("value").value(o[3]);
                w.endObject();
            }
            w.endArray();
        }
        w.endObject();
    }

    /** Writes the item or fluid contents of one capability map. */
    private void writeContents(JsonWriter w, Map<?, ?> map, String capability, boolean input, boolean perTick,
        Integer[] circuit) throws Exception {
        if (map == null) {
            return;
        }
        refs.perTick = perTick;
        try {
            writeContentsOf(w, map, capability, input, circuit);
        } finally {
            refs.perTick = false;
        }
    }

    private void writeContentsOf(JsonWriter w, Map<?, ?> map, String capability, boolean input, Integer[] circuit)
        throws Exception {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!capability.equals(capabilityName(e.getKey()))) {
                continue;
            }
            for (Object content : (Collection<?>) e.getValue()) {
                Object value = part(content, "content");
                double chance = chance(content);
                boolean consumed = !input || chance != 0;
                double outChance = input ? 1 : chance;
                if (capability.equals("item")) {
                    Integer config = circuitNumber(value);
                    if (config != null && input) {
                        circuit[0] = config;
                        continue;
                    }
                    if (value instanceof Ingredient) {
                        refs.ingredient(w, (Ingredient) value, amount(value, 1), outChance, consumed);
                    } else {
                        w.nullValue();
                    }
                } else {
                    writeFluid(w, value, outChance, consumed);
                }
            }
        }
    }

    private void writeFluid(JsonWriter w, Object value, double chance, boolean consumed) throws Exception {
        if (value instanceof FluidStack) {
            refs.fluid(w, (FluidStack) value, chance, consumed);
            return;
        }
        Method stacks = method(value, "getStacks");
        if (stacks == null) {
            w.nullValue();
            return;
        }
        FluidStack[] options = (FluidStack[]) stacks.invoke(value);
        int amount = amount(value, options.length > 0 ? options[0].getAmount() : 0);
        refs.fluids(w, options, amount, fluidTag(value), chance, consumed);
    }

    /** "forge:water" for a fluid ingredient that is a single tag. */
    private static String fluidTag(Object ingredient) {
        try {
            JsonElement json = (JsonElement) ingredient.getClass().getMethod("toJson").invoke(ingredient);
            JsonElement value = json != null && json.isJsonObject() && json.getAsJsonObject().has("value")
                ? json.getAsJsonObject().get("value")
                : json;
            if (value != null && value.isJsonArray() && value.getAsJsonArray().size() == 1) {
                value = value.getAsJsonArray().get(0);
            }
            if (value != null && value.isJsonObject() && value.getAsJsonObject().has("tag")) {
                return value.getAsJsonObject().get("tag").getAsString();
            }
        } catch (Throwable ignored) {
            // no JSON form
        }
        return null;
    }

    /** Total EU per tick in a capability map (EnergyStack voltage x amperage, or a plain number). */
    private long energy(Map<?, ?> map) throws Exception {
        if (map == null) {
            return 0;
        }
        long total = 0;
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (!"eu".equals(capabilityName(e.getKey()))) {
                continue;
            }
            for (Object content : (Collection<?>) e.getValue()) {
                Object value = part(content, "content");
                if (value instanceof Number) {
                    total += ((Number) value).longValue();
                } else if (value != null) {
                    Method m = method(value, "getTotalEU");
                    if (m != null) {
                        total += ((Number) m.invoke(value)).longValue();
                    }
                }
            }
        }
        return total;
    }

    private void collectOther(Map<?, ?> map, String io, boolean perTick, List<String[]> out) throws Exception {
        if (map == null) {
            return;
        }
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String name = capabilityName(e.getKey());
            if (name.equals("item") || name.equals("fluid") || name.equals("eu")) {
                continue;
            }
            for (Object content : (Collection<?>) e.getValue()) {
                out.add(new String[] { name, io, perTick ? "1" : null, String.valueOf(part(content, "content")) });
            }
        }
    }

    /** "item", "fluid", "eu", "cwu"...: the capability's name (older versions) or id path (7.x). */
    private static String capabilityName(Object capability) {
        try {
            Field f = capability.getClass().getField("name");
            Object v = f.get(capability);
            if (v instanceof String) {
                return (String) v;
            }
        } catch (Throwable ignored) {
            // 7.x has an id instead
        }
        try {
            Object id = capability.getClass().getField("id").get(capability);
            if (id instanceof ResourceLocation) {
                return ((ResourceLocation) id).getPath();
            }
        } catch (Throwable ignored) {
            // fall through
        }
        return String.valueOf(capability);
    }

    /** Chance as 0-1: 7.x stores chance out of maxChance; older versions a float. */
    private static double chance(Object content) {
        Object chance = part(content, "chance");
        if (chance == null) {
            return 1;
        }
        if (chance instanceof Float || chance instanceof Double) {
            return ((Number) chance).doubleValue();
        }
        Object max = part(content, "maxChance");
        double maxChance = max instanceof Number && ((Number) max).doubleValue() > 0 ? ((Number) max).doubleValue()
            : 10000;
        return Math.min(1, ((Number) chance).doubleValue() / maxChance);
    }

    /** Programmed circuit number, or null when this is not a circuit ingredient. */
    private static Integer circuitNumber(Object ingredient) {
        if (ingredient == null || !ingredient.getClass().getSimpleName().contains("Circuit")) {
            return null;
        }
        for (String name : new String[] { "getConfiguration", "configuration" }) {
            Method m = method(ingredient, name);
            if (m != null) {
                try {
                    return ((Number) m.invoke(ingredient)).intValue();
                } catch (Throwable ignored) {
                    // try the next name
                }
            }
        }
        return null;
    }

    private static int amount(Object value, int fallback) {
        Method m = method(value, "getAmount");
        if (m == null) {
            m = method(value, "amount");
        }
        try {
            return m != null ? ((Number) m.invoke(value)).intValue() : fallback;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** A record component (7.x) or public field (older): content, chance, maxChance. */
    private static Object part(Object o, String name) {
        if (o == null) {
            return null;
        }
        Method m = method(o, name);
        try {
            if (m != null) {
                return m.invoke(o);
            }
            return o.getClass().getField(name).get(o);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Method method(Object o, String name) {
        try {
            return o.getClass().getMethod(name);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Public field of the recipe by name, cached. */
    private Object get(Object recipe, String name) throws IllegalAccessException {
        Field f = fields.get(name);
        if (f == null && !fields.containsKey(name)) {
            try {
                f = recipeClass.getField(name);
            } catch (NoSuchFieldException e) {
                f = null;
            }
            fields.put(name, f);
        }
        return f == null ? null : f.get(recipe);
    }
}
