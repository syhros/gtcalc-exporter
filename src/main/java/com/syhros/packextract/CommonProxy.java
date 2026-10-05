package com.syhros.packextract;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/** On a dedicated server the mod does nothing. */
public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        PackExtract.LOG.info("Pack Extract is client-only; it does nothing on a dedicated server.");
    }

    public void init(FMLInitializationEvent event) {}
}
