package com.syhros.packextract.client;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.syhros.packextract.CommonProxy;
import com.syhros.packextract.PackExtract;
import com.syhros.packextract.export.ExportJob;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    private static ExportJob.Settings pending;
    private static boolean autoStarted;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ClientConfig.load(event.getSuggestedConfigurationFile());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new ExportCommand());
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        if (ClientConfig.exportOnMainMenu) {
            PackExtract.LOG.info("Pack Extract will export as soon as the main menu opens");
        }
    }

    /** Builds export settings from the config. */
    public static ExportJob.Settings settings(boolean images) {
        Minecraft mc = Minecraft.getMinecraft();
        ExportJob.Settings s = new ExportJob.Settings();
        File out = new File(ClientConfig.outputDir);
        s.outputRoot = out.isAbsolute() ? out : new File(mc.mcDataDir, ClientConfig.outputDir);
        s.packName = ClientConfig.packName.isEmpty() ? instanceName(mc.mcDataDir) : ClientConfig.packName;
        s.images = images && ClientConfig.images;
        s.imageSize = ClientConfig.imageSize;
        s.neiItems = ClientConfig.neiItems;
        return s;
    }

    /** ".../GT_New_Horizons_2.8.4/.minecraft" gives "GT_New_Horizons_2.8.4". */
    static String instanceName(File dataDir) {
        File dir = dataDir.getAbsoluteFile();
        if (dir.getName().equals(".")) {
            dir = dir.getParentFile();
        }
        String name = dir.getName();
        if ((name.equals(".minecraft") || name.equals("minecraft")) && dir.getParentFile() != null) {
            name = dir.getParentFile().getName();
        }
        return name.isEmpty() ? "pack" : name;
    }

    /** Called by the command: opens the export screen on the next tick, once the chat screen has closed. */
    static void startNextTick(ExportJob.Settings settings) {
        pending = settings;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.currentScreen == null) {
            ExportJob.Settings s = pending;
            pending = null;
            mc.displayGuiScreen(new ExportScreen(new ExportJob(s), null, false));
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (ClientConfig.exportOnMainMenu && !autoStarted && event.gui instanceof GuiMainMenu) {
            autoStarted = true;
            PackExtract.LOG.info("Starting the main-menu export");
            event.gui = new ExportScreen(new ExportJob(settings(true)), event.gui,
                ClientConfig.quitAfterMainMenuExport);
        }
    }
}
