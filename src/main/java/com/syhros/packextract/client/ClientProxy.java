package com.syhros.packextract.client;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;

import com.syhros.packextract.CommonProxy;
import com.syhros.packextract.PackExtract;
import com.syhros.packextract.Tags;
import com.syhros.packextract.core.Config;
import com.syhros.packextract.core.ExportJob;
import com.syhros.packextract.export.Platform1710;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class ClientProxy extends CommonProxy {

    private static final String AUTO_WORLD = "packextract-auto";

    private enum Auto {
        OFF,
        WAIT_MENU,
        WAIT_WORLD,
        STARTED
    }

    private static ExportJob.Settings pending;
    private static Auto auto = Auto.OFF;
    private static int autoTicks;

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        ClientConfig.load(event.getSuggestedConfigurationFile());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        ClientCommandHandler.instance.registerCommand(new ExportCommand());
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance().bus().register(this);
        if (ClientConfig.autoExport) {
            PackExtract.LOG.info("Pack Extract will export automatically once a world has loaded");
        }
    }

    /** Builds export settings from the config. */
    public static ExportJob.Settings settings(boolean images) {
        Minecraft mc = Minecraft.getMinecraft();
        ExportJob.Settings s = new ExportJob.Settings();
        File out = new File(ClientConfig.outputDir);
        s.outputRoot = out.isAbsolute() ? out : new File(mc.mcDataDir, ClientConfig.outputDir);
        s.packName = ClientConfig.packName.isEmpty() ? Config.instanceName(mc.mcDataDir) : ClientConfig.packName;
        s.images = images && ClientConfig.images;
        s.imageSize = ClientConfig.imageSize;
        s.viewerItems = ClientConfig.neiItems;
        s.maxItems = ClientConfig.maxItems;
        s.generator = "Pack Extract " + Tags.VERSION;
        return s;
    }

    static ExportJob job(ExportJob.Settings s) {
        return new ExportJob(new Platform1710(s.viewerItems), s);
    }

    /** Called by the command: opens the export screen on the next tick, once the chat screen has closed. */
    static void startNextTick(ExportJob.Settings settings) {
        pending = settings;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (auto == Auto.WAIT_MENU) {
            tickAutoWorld(mc);
        } else if (auto == Auto.WAIT_WORLD) {
            tickAutoExport(mc);
        }
        if (pending == null) {
            return;
        }
        if (mc.currentScreen == null) {
            ExportJob.Settings s = pending;
            pending = null;
            mc.displayGuiScreen(new ExportScreen(job(s), null, false));
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (ClientConfig.autoExport && auto == Auto.OFF && event.gui instanceof GuiMainMenu) {
            auto = Auto.WAIT_MENU;
            autoTicks = 0;
        }
    }

    /**
     * Opens (or creates) a flat creative world once the main menu has been up for a second. GregTech and NEI only
     * finish setting up their rendering and item lists inside a world. Not done inside GuiOpenEvent: the first main
     * menu opens while the game is still initialising.
     */
    private static void tickAutoWorld(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiMainMenu) || ++autoTicks < 20) {
            return;
        }
        auto = Auto.WAIT_WORLD;
        autoTicks = 0;
        PackExtract.LOG.info("Automatic export: loading world {}", AUTO_WORLD);
        try {
            WorldSettings settings = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false,
                WorldType.FLAT);
            settings.enableCommands();
            mc.launchIntegratedServer(AUTO_WORLD, AUTO_WORLD, settings);
        } catch (Throwable t) {
            PackExtract.LOG.error("Automatic export: could not open a world", t);
            auto = Auto.STARTED;
        }
    }

    /** Waits for the world and for NEI's item list, then starts the export. */
    private static void tickAutoExport(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }
        autoTicks++;
        boolean neiReady = neiItemListReady();
        if (autoTicks < 100 || (!neiReady && autoTicks < 1200)) {
            return;
        }
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        auto = Auto.STARTED;
        PackExtract.LOG.info("Automatic export: starting (NEI item list {})", neiReady ? "ready" : "not loaded");
        mc.displayGuiScreen(new ExportScreen(job(settings(true)), null, ClientConfig.quitAfterAutoExport));
    }

    private static boolean neiItemListReady() {
        try {
            Class<?> itemList = Class.forName("codechicken.nei.ItemList");
            return Boolean.TRUE.equals(itemList.getField("loadFinished").get(null));
        } catch (ClassNotFoundException e) {
            return true;
        } catch (Throwable t) {
            return true;
        }
    }
}
