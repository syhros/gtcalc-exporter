package com.syhros.packextract.export;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.oredict.OreDictionary;

import com.syhros.packextract.PackExtract;

/** Fills the item index from everything that lists items, except recipes (they add theirs as they are written). */
public final class ItemSources {

    private ItemSources() {}

    /** Every registered item's sub-items, from every creative tab it is in and from no tab. */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static int fromRegistry(ItemIndex index, Problems problems) {
        int before = index.size();
        for (Object o : Item.itemRegistry) {
            Item item = (Item) o;
            List<ItemStack> subs = new ArrayList<ItemStack>();
            try {
                CreativeTabs[] tabs = item.getCreativeTabs();
                if (tabs == null || tabs.length == 0) {
                    tabs = new CreativeTabs[] { item.getCreativeTab() };
                }
                for (CreativeTabs tab : tabs) {
                    item.getSubItems(item, tab, (List) subs);
                }
                if (subs.isEmpty()) {
                    subs.add(new ItemStack(item, 1, 0));
                }
            } catch (Throwable t) {
                problems.add("sub-items of " + ItemIndex.registryName(item), t);
                if (subs.isEmpty()) {
                    subs.add(new ItemStack(item, 1, 0));
                }
            }
            for (ItemStack s : subs) {
                index.add(s, "registry");
            }
        }
        return index.size() - before;
    }

    /**
     * NotEnoughItems' item list, which adds the variants NEI generates (enchanted books, potions and so on). Read by
     * reflection; skipped when NEI is not installed or has not loaded its list yet (it loads on joining a world).
     */
    public static int fromNei(ItemIndex index, Problems problems) {
        try {
            Class<?> itemList = Class.forName("codechicken.nei.ItemList");
            Object finished = itemList.getField("loadFinished").get(null);
            List<?> items = (List<?>) itemList.getField("items").get(null);
            if (!Boolean.TRUE.equals(finished) || items == null || items.isEmpty()) {
                problems.note("NEI item list not loaded (it loads when you join a world); skipped");
                return 0;
            }
            int before = index.size();
            for (Object o : new ArrayList<Object>(items)) {
                if (o instanceof ItemStack) {
                    index.add((ItemStack) o, "nei");
                }
            }
            return index.size() - before;
        } catch (ClassNotFoundException e) {
            return 0;
        } catch (Throwable t) {
            problems.add("NEI item list", t);
            return 0;
        }
    }

    public static int fromOreDictionary(ItemIndex index) {
        int before = index.size();
        for (String name : OreDictionary.getOreNames()) {
            for (ItemStack s : OreDictionary.getOres(name)) {
                index.add(s, "oredict");
            }
        }
        return index.size() - before;
    }

    public static int fromFluidContainers(ItemIndex index) {
        int before = index.size();
        for (FluidContainerRegistry.FluidContainerData d : FluidContainerRegistry.getRegisteredFluidContainerData()) {
            index.add(d.filledContainer, "fluid-container");
            index.add(d.emptyContainer, "fluid-container");
        }
        return index.size() - before;
    }

    static void log(String what, int added) {
        PackExtract.LOG.info("{}: {} new items", what, added);
    }
}
