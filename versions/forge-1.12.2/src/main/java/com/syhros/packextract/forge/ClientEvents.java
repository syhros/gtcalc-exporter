package com.syhros.packextract.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.syhros.packextract.core.ExportJob;

/** The progress screen and the automatic export for scripted runs. */
public class ClientEvents {

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

    static ExportJob.Settings settings(boolean images) {
        return PackExtractMod.config.settings(Minecraft.getMinecraft().gameDir, images, "Pack Extract " + Tags.VERSION);
    }

    static ExportJob job(ExportJob.Settings s) {
        return new ExportJob(new Platform1122(s.viewerItems), s);
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
        if (pending != null && mc.currentScreen == null) {
            ExportJob.Settings s = pending;
            pending = null;
            mc.displayGuiScreen(new ExportScreen(job(s), false));
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (PackExtractMod.config.autoExport && auto == Auto.OFF && event.getGui() instanceof GuiMainMenu) {
            auto = Auto.WAIT_MENU;
            autoTicks = 0;
        }
    }

    /** Opens (or creates) a flat creative world once the main menu has been up for a second. */
    private static void tickAutoWorld(Minecraft mc) {
        if (!(mc.currentScreen instanceof GuiMainMenu) || ++autoTicks < 20) {
            return;
        }
        auto = Auto.WAIT_WORLD;
        autoTicks = 0;
        PackExtractMod.LOG.info("Automatic export: loading world {}", AUTO_WORLD);
        try {
            if (mc.getSaveLoader().canLoadWorld(AUTO_WORLD)) {
                mc.launchIntegratedServer(AUTO_WORLD, AUTO_WORLD, null);
            } else {
                WorldSettings settings = new WorldSettings(0L, GameType.CREATIVE, false, false, WorldType.FLAT);
                settings.enableCommands();
                mc.launchIntegratedServer(AUTO_WORLD, AUTO_WORLD, settings);
            }
        } catch (Throwable t) {
            PackExtractMod.LOG.error("Automatic export: could not open a world", t);
            auto = Auto.STARTED;
        }
    }

    /** Waits for the world (and JEI, when installed), then starts the export. */
    private static void tickAutoExport(Minecraft mc) {
        if (mc.world == null || mc.player == null) {
            return;
        }
        autoTicks++;
        boolean jeiReady = !Loader.isModLoaded("jei") || JeiItems.ready();
        if (autoTicks < 100 || (!jeiReady && autoTicks < 1200)) {
            return;
        }
        if (mc.currentScreen != null) {
            mc.displayGuiScreen(null);
        }
        auto = Auto.STARTED;
        PackExtractMod.LOG.info("Automatic export: starting (JEI {})", jeiReady ? "ready" : "not ready");
        mc.displayGuiScreen(new ExportScreen(job(settings(true)), PackExtractMod.config.quitAfterAutoExport));
    }
}
