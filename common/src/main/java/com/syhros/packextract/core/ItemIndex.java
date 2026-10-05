package com.syhros.packextract.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every distinct item seen during the export, keyed by id. The game's own stack object is kept as {@code stack}
 * (an {@code ItemStack} of whichever Minecraft version is running) so the platform code can render and describe it.
 */
public final class ItemIndex {

    public static final class Entry {

        public final String id;
        public final String registryName;
        /** Damage value on 1.7.10 to 1.12.2; null on 1.13+, where items have no meta. */
        public final Integer meta;
        /** NBT (or 1.20.5+ data components) as text, or null. */
        public final String nbt;
        public final Object stack;
        public final Set<String> sources = new LinkedHashSet<String>();
        public String image;
        public boolean imageBlank;
        public String imageError;

        Entry(String id, String registryName, Integer meta, String nbt, Object stack) {
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

    /** Makes a one-item copy of the game's stack, only called the first time an id is seen. */
    public interface Copier {

        Object copy();
    }

    private final Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
    private final Map<String, List<String>> variants = new HashMap<String, List<String>>();

    /** Records an item (once per id) and returns its id. */
    public String add(String id, String registryName, Integer meta, String nbt, Copier copier, String source) {
        Entry e = byId.get(id);
        if (e == null) {
            e = new Entry(id, registryName, meta, nbt, copier.copy());
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
