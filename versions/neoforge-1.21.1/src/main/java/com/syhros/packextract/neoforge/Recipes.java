package com.syhros.packextract.neoforge;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;

import com.google.gson.stream.JsonWriter;
import com.syhros.packextract.core.RecipeSource;
import com.syhros.packextract.export.JsonArrayFile;
import com.syhros.packextract.export.Problems;

/**
 * Recipe files for 1.21.1, all read from the recipe manager (the integrated server's in single player, which has
 * every recipe; the client's copy on a server):
 *
 * <ul>
 * <li>recipes/crafting.json: crafting table recipes</li>
 * <li>recipes/smelting.json: furnace, blast furnace, smoker and campfire recipes</li>
 * <li>recipes/gregtech.json: GregTech CEu Modern recipes, when it is installed ({@link GregTechRecipes})</li>
 * <li>recipes/other.json: every other recipe type (Create, Mekanism, Thermal...), as ingredients and result</li>
 * </ul>
 */
final class Recipes {

    private final Refs refs;
    private final Problems problems;
    private final RegistryAccess registries;
    private final Map<ResourceLocation, List<Recipe<?>>> byType = new LinkedHashMap<>();
    /** 1.21 recipes do not know their id; the recipe manager keeps it beside them. */
    private static final Map<Recipe<?>, ResourceLocation> IDS = new IdentityHashMap<>();

    Recipes(Refs refs, Problems problems) {
        this.refs = refs;
        this.problems = problems;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        RecipeManager manager = server != null ? server.getRecipeManager() : mc.level.getRecipeManager();
        this.registries = server != null ? server.registryAccess() : mc.level.registryAccess();
        IDS.clear();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            Recipe<?> r = holder.value();
            IDS.put(r, holder.id());
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(r.getType());
            byType.computeIfAbsent(type != null ? type : ResourceLocation.fromNamespaceAndPath("unknown", "unknown"),
                k -> new ArrayList<>()).add(r);
        }
    }

    List<RecipeSource> sources() {
        List<RecipeSource> list = new ArrayList<>();
        list.add(new Crafting());
        list.add(new Cooking());
        GregTechRecipes gt = GregTechRecipes.create(refs, problems);
        if (byType.values().stream().anyMatch(gt::handles)) {
            list.add(gt.source(byType));
        }
        list.add(new Other(gt));
        return list;
    }

    static String id(Recipe<?> r) {
        ResourceLocation id = IDS.get(r);
        return id != null ? id.toString() : "unknown";
    }

    private List<Recipe<?>> ofType(RecipeType<?> type) {
        List<Recipe<?>> list = byType.get(BuiltInRegistries.RECIPE_TYPE.getKey(type));
        return list != null ? list : new ArrayList<>();
    }

    private ItemStack result(Recipe<?> r) {
        try {
            return r.getResultItem(registries);
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    /** Crafting table recipes: shaped (with width and height), shapeless, and mods' special recipes. */
    private final class Crafting extends Single {

        private int standard;
        private int special;

        Crafting() {
            super("recipes/crafting.json", "craftingRecipes", "Crafting recipes");
        }

        @Override
        public void writePart(int index, JsonArrayFile out) throws IOException {
            for (Recipe<?> r : ofType(RecipeType.CRAFTING)) {
                boolean[] isStandard = new boolean[1];
                if (out.add(w -> isStandard[0] = writeCrafting(w, r), problems, "crafting recipe " + id(r))) {
                    if (isStandard[0]) {
                        standard++;
                    } else {
                        special++;
                    }
                }
            }
        }

        private boolean writeCrafting(JsonWriter w, Recipe<?> r) throws IOException {
            NonNullList<Ingredient> inputs = r.getIngredients();
            ItemStack output = result(r);
            boolean special = r.isSpecial() || inputs.isEmpty();
            w.beginObject();
            w.name("id").value(id(r));
            if (special) {
                w.name("type").value("special");
            } else if (r instanceof ShapedRecipe) {
                ShapedRecipe s = (ShapedRecipe) r;
                w.name("type").value("shaped");
                w.name("width").value(s.getWidth());
                w.name("height").value(s.getHeight());
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

        @Override
        void counts(Map<String, Number> counts, int written) {
            counts.put("craftingRecipes", standard);
            counts.put("craftingSpecialRecipes", special);
        }
    }

    /** Furnace, blast furnace, smoker and campfire recipes. */
    private final class Cooking extends Single {

        Cooking() {
            super("recipes/smelting.json", "smeltingRecipes", "Furnace recipes");
        }

        @Override
        public void writePart(int index, JsonArrayFile out) throws IOException {
            RecipeType<?>[] types = { RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING,
                RecipeType.CAMPFIRE_COOKING };
            for (RecipeType<?> type : types) {
                String typeName = BuiltInRegistries.RECIPE_TYPE.getKey(type).getPath();
                for (Recipe<?> r : ofType(type)) {
                    out.add(w -> {
                        w.beginObject();
                        w.name("id").value(id(r));
                        w.name("type").value(typeName);
                        w.name("input");
                        refs.ingredient(w, r.getIngredients().isEmpty() ? null : r.getIngredients().get(0), 1, 1,
                            true);
                        w.name("output");
                        refs.stack(w, result(r), 1, true);
                        if (r instanceof AbstractCookingRecipe) {
                            AbstractCookingRecipe c = (AbstractCookingRecipe) r;
                            w.name("experience").value(c.getExperience());
                            w.name("ticks").value(c.getCookingTime());
                        }
                        w.endObject();
                    }, problems, typeName + " recipe " + id(r));
                }
            }
        }

        @Override
        void counts(Map<String, Number> counts, int written) {
            counts.put("smeltingRecipes", written);
        }
    }

    /**
     * Every other recipe type, one part per type: its id, ingredients and result as the game's recipe interface
     * gives them. Fluids and chances of mods' own machines are not part of that interface.
     */
    private final class Other implements RecipeSource {

        private final List<ResourceLocation> types = new ArrayList<>();

        Other(GregTechRecipes gt) {
            for (ResourceLocation type : byType.keySet()) {
                String path = type.toString();
                boolean vanilla = type.getNamespace().equals("minecraft")
                    && (path.equals("minecraft:crafting") || path.equals("minecraft:smelting")
                        || path.equals("minecraft:blasting") || path.equals("minecraft:smoking")
                        || path.equals("minecraft:campfire_cooking"));
                if (!vanilla && !gt.handles(byType.get(type))) {
                    types.add(type);
                }
            }
        }

        @Override
        public String file() {
            return "recipes/other.json";
        }

        @Override
        public String manifestKey() {
            return "otherRecipes";
        }

        @Override
        public String label() {
            return "Other recipes";
        }

        @Override
        public int parts() {
            return types.size();
        }

        @Override
        public void writePart(int index, JsonArrayFile out) throws IOException {
            ResourceLocation type = types.get(index);
            for (Recipe<?> r : byType.get(type)) {
                out.add(w -> {
                    w.beginObject();
                    w.name("type").value(type.toString());
                    w.name("id").value(id(r));
                    w.name("inputs");
                    w.beginArray();
                    for (Ingredient in : r.getIngredients()) {
                        refs.ingredient(w, in, 1, 1, true);
                    }
                    w.endArray();
                    ItemStack output = result(r);
                    w.name("output");
                    if (output.isEmpty()) {
                        w.nullValue();
                    } else {
                        refs.stack(w, output, 1, true);
                    }
                    w.name("class").value(r.getClass().getName());
                    w.endObject();
                }, problems, type + " recipe " + id(r));
            }
        }

        @Override
        public void finish(File dir, JsonArrayFile out, Map<String, Number> counts, Map<String, String> files) {
            counts.put("otherRecipes", out.count());
            counts.put("otherRecipeTypes", types.size());
        }
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
