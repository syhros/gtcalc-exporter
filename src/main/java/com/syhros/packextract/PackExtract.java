package com.syhros.packextract;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

/**
 * Exports every item, fluid, icon and recipe of the running modpack. Client-side only: it renders icons, and it does
 * not need to be installed on the server.
 */
@Mod(
    modid = PackExtract.MODID,
    name = "Pack Extract",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.7.10]",
    acceptableRemoteVersions = "*")
public class PackExtract {

    public static final String MODID = "packextract";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.syhros.packextract.client.ClientProxy",
        serverSide = "com.syhros.packextract.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }
}
