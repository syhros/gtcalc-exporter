package com.syhros.packextract.export;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.stream.JsonWriter;

/**
 * GregTech 5 (GT5-Unofficial, as in GT New Horizons) recipe maps, read by reflection so no GregTech jar is needed to
 * build and one jar covers old and new GT5 versions.
 *
 * <p>
 * Newer GT5 (5.09.45+): {@code gregtech.api.recipe.RecipeMap.ALL_RECIPE_MAPS}. Older:
 * {@code GT_Recipe.GT_Recipe_Map.sMappings}. Recipe fields ({@code mInputs}, {@code mOutputs}, {@code mFluidInputs},
 * {@code mFluidOutputs}, {@code mDuration}, {@code mEUt}, {@code mSpecialValue}) have the same names in both.
 */
public final class GregTechRecipes {

    /** One recipe map: its id, display name and recipes. */
    public static final class RecipeMapInfo {

        public final String id;
        public final String name;
        public final Collection<?> recipes;
        public int written;

        RecipeMapInfo(String id, String name, Collection<?> recipes) {
            this.id = id;
            this.name = name;
            this.recipes = recipes;
        }
    }

    private final Map<Class<?>, Map<String, Field>> fields = new HashMap<Class<?>, Map<String, Field>>();
    private final Map<Class<?>, Method> chanceMethods = new HashMap<Class<?>, Method>();

    /** All recipe maps, or an empty list when GregTech 5 is not installed. */
    public static List<RecipeMapInfo> maps(Problems problems) {
        List<RecipeMapInfo> out = new ArrayList<RecipeMapInfo>();
        try {
            Class<?> rm = Class.forName("gregtech.api.recipe.RecipeMap");
            Map<?, ?> all = (Map<?, ?>) rm.getField("ALL_RECIPE_MAPS").get(null);
            Field nameField = rm.getField("unlocalizedName");
            Method recipes = rm.getMethod("getAllRecipes");
            List<Object> sorted = new ArrayList<Object>(all.values());
            for (Object map : sorted) {
                try {
                    String id = (String) nameField.get(map);
                    out.add(new RecipeMapInfo(id, localize(id), (Collection<?>) recipes.invoke(map)));
                } catch (Throwable t) {
                    problems.add("GregTech recipe map", t);
                }
            }
            return out;
        } catch (ClassNotFoundException e) {
            // fall through to the older API
        } catch (Throwable t) {
            problems.add("GregTech recipe maps", t);
            return out;
        }
        try {
            Class<?> legacy = Class.forName("gregtech.api.util.GT_Recipe$GT_Recipe_Map");
            Collection<?> all = (Collection<?>) legacy.getField("sMappings").get(null);
            for (Object map : all) {
                try {
                    String id = (String) legacy.getField("mUnlocalizedName").get(map);
                    Collection<?> list = (Collection<?>) legacy.getField("mRecipeList").get(map);
                    out.add(new RecipeMapInfo(id, localize(id), list));
                } catch (Throwable t) {
                    problems.add("GregTech recipe map (old API)", t);
                }
            }
        } catch (ClassNotFoundException e) {
            // GregTech 5 is not installed.
        } catch (Throwable t) {
            problems.add("GregTech recipe maps (old API)", t);
        }
        return out;
    }

    private static String localize(String key) {
        String s = StatCollector.translateToLocal(key);
        return s == null || s.isEmpty() ? key : s;
    }

    /** Writes every recipe of one map, one JSON object each. */
    public void writeMap(JsonArrayFile out, Refs refs, Problems problems, RecipeMapInfo map) throws IOException {
        int index = 0;
        for (final Object recipe : new ArrayList<Object>(map.recipes)) {
            final int i = index++;
            if (out.add(w -> writeRecipe(w, refs, map, i, recipe), problems, "GregTech recipe in " + map.id)) {
                map.written++;
            }
        }
    }

    private void writeRecipe(JsonWriter w, Refs refs, RecipeMapInfo map, int index, Object r) throws Exception {
        ItemStack[] inputs = (ItemStack[]) get(r, "mInputs");
        ItemStack[] outputs = (ItemStack[]) get(r, "mOutputs");
        FluidStack[] fluidInputs = (FluidStack[]) get(r, "mFluidInputs");
        FluidStack[] fluidOutputs = (FluidStack[]) get(r, "mFluidOutputs");
        int[] inputChances = (int[]) get(r, "mInputChances");
        int[] fluidInputChances = (int[]) get(r, "mFluidInputChances");
        int[] fluidOutputChances = (int[]) get(r, "mFluidOutputChances");

        w.beginObject();
        w.name("map").value(map.id);
        w.name("index").value(index);

        w.name("inputs");
        w.beginArray();
        if (inputs != null) {
            for (int i = 0; i < inputs.length; i++) {
                if (inputs[i] != null) {
                    refs.write(w, inputs[i], chance(inputChances, i), "recipe");
                }
            }
        }
        w.endArray();

        w.name("outputs");
        w.beginArray();
        if (outputs != null) {
            for (int i = 0; i < outputs.length; i++) {
                if (outputs[i] != null) {
                    refs.write(w, outputs[i], outputChance(r, i), "recipe");
                }
            }
        }
        w.endArray();

        w.name("fluidInputs");
        w.beginArray();
        if (fluidInputs != null) {
            for (int i = 0; i < fluidInputs.length; i++) {
                if (fluidInputs[i] != null) {
                    refs.writeFluid(w, fluidInputs[i], chance(fluidInputChances, i));
                }
            }
        }
        w.endArray();

        w.name("fluidOutputs");
        w.beginArray();
        if (fluidOutputs != null) {
            for (int i = 0; i < fluidOutputs.length; i++) {
                if (fluidOutputs[i] != null) {
                    refs.writeFluid(w, fluidOutputs[i], chance(fluidOutputChances, i));
                }
            }
        }
        w.endArray();

        w.name("eut").value(number(get(r, "mEUt")));
        w.name("duration").value(number(get(r, "mDuration")));
        w.name("specialValue").value(number(get(r, "mSpecialValue")));
        flag(w, r, "enabled", "mEnabled");
        flag(w, r, "hidden", "mHidden");
        flag(w, r, "fake", "mFakeRecipe");

        Object special = get(r, "mSpecialItems");
        if (special instanceof ItemStack || special instanceof ItemStack[] || special instanceof List) {
            w.name("specialItems");
            refs.write(w, special, 1, "recipe");
        }
        writeMetadata(w, r);
        w.endObject();
    }

    /** Output chance from {@code getOutputChance(i)} (both GT versions), in 0-1. */
    private double outputChance(Object r, int i) {
        try {
            Method m = chanceMethods.get(r.getClass());
            if (m == null && !chanceMethods.containsKey(r.getClass())) {
                try {
                    m = r.getClass().getMethod("getOutputChance", int.class);
                } catch (NoSuchMethodException e) {
                    m = null;
                }
                chanceMethods.put(r.getClass(), m);
            }
            if (m != null) {
                return ((Number) m.invoke(r, i)).intValue() / 10000.0;
            }
            int[] chances = (int[]) get(r, "mChances");
            return chance(chances, i);
        } catch (Throwable t) {
            return 1;
        }
    }

    private static double chance(int[] chances, int i) {
        if (chances == null || i >= chances.length || chances[i] <= 0) {
            return 1;
        }
        return Math.min(1, chances[i] / 10000.0);
    }

    /** Recipe metadata (newer GT5): coil heat, fusion threshold and so on, by identifier. */
    private void writeMetadata(JsonWriter w, Object r) throws Exception {
        Object storage;
        try {
            storage = r.getClass().getMethod("getMetadataStorage").invoke(r);
        } catch (NoSuchMethodException e) {
            return;
        }
        if (storage == null) {
            return;
        }
        Collection<?> entries = (Collection<?>) storage.getClass().getMethod("getEntries").invoke(storage);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        w.name("metadata");
        w.beginObject();
        for (Object o : entries) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) o;
            String key = metadataKeyName(e.getKey());
            Object v = e.getValue();
            w.name(key);
            if (v instanceof Number) {
                w.value((Number) v);
            } else if (v instanceof Boolean) {
                w.value((Boolean) v);
            } else if (v == null) {
                w.nullValue();
            } else {
                w.value(String.valueOf(v));
            }
        }
        w.endObject();
    }

    private static String metadataKeyName(Object key) {
        for (Class<?> c = key.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField("identifier");
                f.setAccessible(true);
                return String.valueOf(f.get(key));
            } catch (NoSuchFieldException e) {
                // keep looking in the superclass
            } catch (Throwable t) {
                break;
            }
        }
        return String.valueOf(key);
    }

    private void flag(JsonWriter w, Object r, String name, String field) throws Exception {
        Object v = get(r, field);
        if (v instanceof Boolean) {
            w.name(name).value((Boolean) v);
        }
    }

    private static Number number(Object o) {
        return o instanceof Number ? (Number) o : 0;
    }

    /** Public field by name (cached per class), or null when the class does not have it. */
    private Object get(Object o, String name) throws IllegalAccessException {
        Map<String, Field> byName = fields.get(o.getClass());
        if (byName == null) {
            byName = new HashMap<String, Field>();
            fields.put(o.getClass(), byName);
        }
        Field f;
        if (byName.containsKey(name)) {
            f = byName.get(name);
        } else {
            try {
                f = o.getClass().getField(name);
            } catch (NoSuchFieldException e) {
                f = null;
            }
            byName.put(name, f);
        }
        return f == null ? null : f.get(o);
    }
}
