package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.crafting.IShapedRecipe;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.JsonArrayFile;
import com.syhros.packextract.export.Problems;

/** Crafting table and furnace recipes on 1.12.2. */
final class Recipes {

    private Recipes() {}

    /**
     * {@code {"id":"minecraft:crafting_table","type":"shaped","width":2,"height":2,"inputs":[...],"output":{...}}}.
     * Recipes with no fixed ingredients (mods' special recipes) are listed as "special".
     */
    static RecipeSource crafting(Refs refs, Problems problems) {
        return new Single("recipes/crafting.json", "craftingRecipes", "Crafting recipes") {

            private int standard;
            private int special;

            @Override
            public void writePart(int index, JsonArrayFile out) throws IOException {
                for (IRecipe r : ForgeRegistries.RECIPES.getValuesCollection()) {
                    boolean[] isStandard = new boolean[1];
                    if (out.add(w -> isStandard[0] = writeCrafting(w, refs, r), problems,
                        "crafting recipe " + r.getRegistryName())) {
                        if (isStandard[0]) {
                            standard++;
                        } else {
                            special++;
                        }
                    }
                }
            }

            @Override
            void counts(Map<String, Number> counts, int written) {
                counts.put("craftingRecipes", standard);
                counts.put("craftingSpecialRecipes", special);
            }
        };
    }

    private static boolean writeCrafting(JsonWriter w, Refs refs, IRecipe r) throws IOException {
        NonNullList<Ingredient> inputs = r.getIngredients();
        ItemStack output = r.getRecipeOutput();
        boolean special = r.isDynamic() || inputs.isEmpty();
        w.beginObject();
        w.name("id").value(String.valueOf(r.getRegistryName()));
        if (special) {
            w.name("type").value("special");
        } else if (r instanceof IShapedRecipe) {
            IShapedRecipe s = (IShapedRecipe) r;
            w.name("type").value("shaped");
            w.name("width").value(s.getRecipeWidth());
            w.name("height").value(s.getRecipeHeight());
        } else {
            w.name("type").value("shapeless");
        }
        if (!inputs.isEmpty()) {
            w.name("inputs");
            w.beginArray();
            for (Ingredient in : inputs) {
                refs.ingredient(w, in, 1, 1, true);
            }
            w.endArray();
        }
        w.name("output");
        if (output.isEmpty()) {
            w.nullValue();
        } else {
            refs.stack(w, output, 1, true);
        }
        w.name("class").value(r.getClass().getName());
        w.endObject();
        return !special;
    }

    /** {@code {"input":{...},"output":{...},"experience":0.7}} per furnace recipe. */
    static RecipeSource smelting(Refs refs, Problems problems) {
        return new Single("recipes/smelting.json", "smeltingRecipes", "Furnace recipes") {

            @Override
            public void writePart(int index, JsonArrayFile out) throws IOException {
                FurnaceRecipes furnace = FurnaceRecipes.instance();
                for (Map.Entry<ItemStack, ItemStack> e : furnace.getSmeltingList().entrySet()) {
                    out.add(w -> {
                        w.beginObject();
                        w.name("input");
                        ItemStack in = e.getKey().copy();
                        in.setCount(1);
                        refs.stack(w, in, 1, true);
                        w.name("output");
                        refs.stack(w, e.getValue(), 1, true);
                        w.name("experience").value(furnace.getSmeltingExperience(e.getValue()));
                        w.endObject();
                    }, problems, "smelting recipe");
                }
            }

            @Override
            void counts(Map<String, Number> counts, int written) {
                counts.put("smeltingRecipes", written);
            }
        };
    }

    /** A recipe file written in one go. */
    abstract static class Single implements RecipeSource {

        private final String file;
        private final String key;
        private final String label;

        Single(String file, String key, String label) {
            this.file = file;
            this.key = key;
            this.label = label;
        }

        @Override
        public String file() {
            return file;
        }

        @Override
        public String manifestKey() {
            return key;
        }

        @Override
        public String label() {
            return label;
        }

        @Override
        public int parts() {
            return 1;
        }

        abstract void counts(Map<String, Number> counts, int written);

        @Override
        public void finish(File dir, JsonArrayFile out, Map<String, Number> counts, Map<String, String> files) {
            counts(counts, out.count());
        }
    }
}
