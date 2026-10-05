package com.syhros.packextract.export;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.item.crafting.ShapelessRecipes;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;

import com.google.gson.stream.JsonWriter;

/** Crafting table and furnace recipes. */
public final class CraftingRecipes {

    private CraftingRecipes() {}

    /**
     * Writes {@code {"type":"shaped","width":3,"height":3,"inputs":[...9],"output":{...}}} per recipe. Recipe classes
     * that are not one of the four standard kinds (mods' own special recipes) are listed with their class name and
     * output only.
     */
    public static int[] writeCrafting(JsonArrayFile out, Refs refs, Problems problems) throws IOException {
        final int[] counts = new int[2]; // standard, special
        for (Object o : CraftingManager.getInstance().getRecipeList()) {
            final IRecipe r = (IRecipe) o;
            if (r == null) {
                continue;
            }
            final boolean[] standard = new boolean[1];
            boolean ok = out.add(w -> standard[0] = writeOne(w, refs, r), problems,
                "crafting recipe " + r.getClass().getName());
            if (ok) {
                counts[standard[0] ? 0 : 1]++;
            }
        }
        return counts;
    }

    private static boolean writeOne(JsonWriter w, Refs refs, IRecipe r) throws IOException {
        ItemStack output = r.getRecipeOutput();
        if (r instanceof ShapedOreRecipe) {
            ShapedOreRecipe s = (ShapedOreRecipe) r;
            writeShaped(w, refs, s.getInput(), intField(s, "width", 3), intField(s, "height", 3), output, r);
            return true;
        }
        if (r instanceof ShapedRecipes) {
            ShapedRecipes s = (ShapedRecipes) r;
            writeShaped(w, refs, s.recipeItems, s.recipeWidth, s.recipeHeight, output, r);
            return true;
        }
        if (r instanceof ShapelessOreRecipe) {
            writeShapeless(w, refs, ((ShapelessOreRecipe) r).getInput(), output, r);
            return true;
        }
        if (r instanceof ShapelessRecipes) {
            writeShapeless(w, refs, ((ShapelessRecipes) r).recipeItems, output, r);
            return true;
        }
        // A mod's own recipe class: its inputs are not readable in a standard way.
        w.beginObject();
        w.name("type").value("special");
        w.name("class").value(r.getClass().getName());
        w.name("output");
        if (output != null) {
            refs.writeStack(w, output, 1, "recipe");
        } else {
            w.nullValue();
        }
        w.endObject();
        return false;
    }

    private static void writeShaped(JsonWriter w, Refs refs, Object[] inputs, int width, int height,
        ItemStack output, IRecipe r) throws IOException {
        w.beginObject();
        w.name("type").value("shaped");
        w.name("width").value(width);
        w.name("height").value(height);
        w.name("inputs");
        w.beginArray();
        for (Object in : inputs) {
            refs.write(w, in instanceof ItemStack ? withAmount((ItemStack) in) : in, 1, "recipe");
        }
        w.endArray();
        w.name("output");
        refs.writeStack(w, output, 1, "recipe");
        w.name("class").value(r.getClass().getName());
        w.endObject();
    }

    private static void writeShapeless(JsonWriter w, Refs refs, List<?> inputs, ItemStack output, IRecipe r)
        throws IOException {
        w.beginObject();
        w.name("type").value("shapeless");
        w.name("inputs");
        w.beginArray();
        for (Object in : inputs) {
            refs.write(w, in instanceof ItemStack ? withAmount((ItemStack) in) : in, 1, "recipe");
        }
        w.endArray();
        w.name("output");
        refs.writeStack(w, output, 1, "recipe");
        w.name("class").value(r.getClass().getName());
        w.endObject();
    }

    /** A crafting grid slot always takes one item, whatever stack size the recipe object stores. */
    private static ItemStack withAmount(ItemStack s) {
        ItemStack c = s.copy();
        c.stackSize = 1;
        return c;
    }

    private static int intField(Object o, String name, int fallback) {
        try {
            Field f = o.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.getInt(o);
        } catch (Throwable t) {
            try {
                Field f = ShapedOreRecipe.class.getDeclaredField(name);
                f.setAccessible(true);
                return f.getInt(o);
            } catch (Throwable t2) {
                return fallback;
            }
        }
    }

    /** Writes {@code {"input":{...},"output":{...},"experience":0.7}} per recipe. */
    @SuppressWarnings("unchecked")
    public static int writeSmelting(JsonArrayFile out, Refs refs, Problems problems) throws IOException {
        java.util.Map<ItemStack, ItemStack> list = FurnaceRecipes.smelting().getSmeltingList();
        for (final java.util.Map.Entry<ItemStack, ItemStack> e : list.entrySet()) {
            out.add(w -> {
                w.beginObject();
                w.name("input");
                refs.writeStack(w, withAmount(e.getKey()), 1, "recipe");
                w.name("output");
                refs.writeStack(w, e.getValue(), 1, "recipe");
                w.name("experience").value(FurnaceRecipes.smelting().func_151398_b(e.getValue()));
                w.endObject();
            }, problems, "smelting recipe");
        }
        return out.count();
    }
}
