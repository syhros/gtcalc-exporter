package com.syhros.packextract.forge;

import java.io.File;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.syhros.packextract.core.Config;

/**
 * Exports every item, fluid, icon, ore dictionary entry and recipe of the running modpack. Client-side only: Forge does
 * not load it on a dedicated server.
 */
@Mod(
    modid = PackExtractMod.MODID,
    name = "Pack Extract",
    version = Tags.VERSION,
    acceptedMinecraftVersions = "[1.12.2]",
    clientSideOnly = true,
    acceptableRemoteVersions = "*")
public class PackExtractMod {

    public static final String MODID = "packextract";
    public static final Logger LOG = LogManager.getLogger(MODID);

    static Config config;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        config = Config.load(new File(event.getModConfigurationDirectory(), "packextract.properties"));
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new ExportCommand());
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
        if (config.autoExport) {
            LOG.info("Pack Extract will export automatically once a world has loaded");
        }
    }
}
