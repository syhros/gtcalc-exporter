package com.syhros.packextract.export;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.syhros.packextract.util.Ids;

/** Every distinct item stack seen during the export, keyed by {@link Ids#item} id. */
public final class ItemIndex {

    public static final class Entry {

        public final String id;
        public final String registryName;
        public final int meta;
        public final String nbt;
        public final ItemStack stack;
        public final Set<String> sources = new LinkedHashSet<String>();
        public String image;
        public boolean imageBlank;
        public String imageError;

        Entry(String id, String registryName, int meta, String nbt, ItemStack stack) {
            this.id = id;
            this.registryName = registryName;
            this.meta = meta;
            this.nbt = nbt;
            this.stack = stack;
        }

        public String mod() {
            int i = registryName.indexOf(':');
            return i < 0 ? "minecraft" : registryName.substring(0, i);
        }
    }

    private final Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
    private final Map<String, List<String>> variants = new HashMap<String, List<String>>();

    public static String registryName(Item item) {
        Object name = Item.itemRegistry.getNameForObject(item);
        return name != null ? name.toString() : "unregistered:" + item.getClass().getName();
    }

    /** The id of a stack without adding it, or null for an empty stack. */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : null;
        return Ids.item(registryName(stack.getItem()), stack.getItemDamage(), nbt);
    }

    /**
     * Records a stack and returns its id. Wildcard stacks ("any damage value") are not recorded as items; their id
     * ends in {@code :*} and {@link #variantsOf} lists the matching items.
     */
    public String add(ItemStack stack, String source) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        String registryName = registryName(stack.getItem());
        int meta = stack.getItemDamage();
        String nbt = stack.hasTagCompound() ? stack.getTagCompound().toString() : null;
        String id = Ids.item(registryName, meta, nbt);
        if (meta == Ids.WILDCARD) {
            return id;
        }
        Entry e = byId.get(id);
        if (e == null) {
            ItemStack copy = stack.copy();
            copy.stackSize = 1;
            e = new Entry(id, registryName, meta, nbt, copy);
            byId.put(id, e);
            List<String> list = variants.get(registryName);
            if (list == null) {
                list = new ArrayList<String>();
                variants.put(registryName, list);
            }
            list.add(id);
        }
        e.sources.add(source);
        return id;
    }

    /** Ids of every recorded variant of an item (for wildcard references). */
    public List<String> variantsOf(String registryName) {
        List<String> list = variants.get(registryName);
        return list != null ? list : new ArrayList<String>();
    }

    public Entry get(String id) {
        return byId.get(id);
    }

    public Collection<Entry> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
