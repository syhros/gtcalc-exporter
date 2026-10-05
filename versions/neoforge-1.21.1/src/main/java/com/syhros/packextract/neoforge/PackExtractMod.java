package com.syhros.packextract.neoforge;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Exports every item, fluid, icon, tag and recipe of the running modpack. Client-side only: it renders icons, and it
 * does nothing on a dedicated server.
 */
@Mod(PackExtractMod.MODID)
public class PackExtractMod {

    public static final String MODID = "packextract";
    public static final Logger LOG = LogManager.getLogger(MODID);

    public PackExtractMod() {
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.init();
        } else {
            LOG.info("Pack Extract is client-only; it does nothing on a dedicated server.");
        }
    }
}
