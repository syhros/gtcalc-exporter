package com.syhros.packextract.export;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/** Every fluid seen during the export, keyed by its registry name. */
public final class FluidIndex {

    public static final class Entry {

        public final String id;
        public final Fluid fluid;
        public final Set<String> sources = new LinkedHashSet<String>();
        public String image;
        public boolean imageBlank;
        public String imageError;

        Entry(String id, Fluid fluid) {
            this.id = id;
            this.fluid = fluid;
        }
    }

    private final Map<String, Entry> byId = new LinkedHashMap<String, Entry>();

    public String add(Fluid fluid, String source) {
        String id = fluid.getName();
        Entry e = byId.get(id);
        if (e == null) {
            e = new Entry(id, fluid);
            byId.put(id, e);
        }
        e.sources.add(source);
        return id;
    }

    public void addRegistered() {
        for (Fluid f : FluidRegistry.getRegisteredFluids().values()) {
            add(f, "registry");
        }
    }

    public Collection<Entry> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
