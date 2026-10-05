package com.syhros.packextract.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Every fluid seen during the export, keyed by its registry name. {@code fluid} is the game's fluid object. */
public final class FluidIndex {

    public static final class Entry {

        public final String id;
        public final Object fluid;
        public final Set<String> sources = new LinkedHashSet<String>();
        public String image;
        public boolean imageBlank;
        public String imageError;

        Entry(String id, Object fluid) {
            this.id = id;
            this.fluid = fluid;
        }
    }

    private final Map<String, Entry> byId = new LinkedHashMap<String, Entry>();

    public String add(String id, Object fluid, String source) {
        Entry e = byId.get(id);
        if (e == null) {
            e = new Entry(id, fluid);
            byId.put(id, e);
        }
        e.sources.add(source);
        return id;
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
