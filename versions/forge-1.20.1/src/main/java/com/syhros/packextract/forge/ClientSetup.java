package com.syhros.packextract.forge;

import java.io.File;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import com.syhros.packextract.core.Config;
import com.syhros.packextract.core.ExportJob;

/** Client side: the /packextract command, the progress screen and the automatic export for scripted runs. */
public final class ClientSetup {

    private static final String AUTO_WORLD = "packextract-auto";

    private enum Auto {
        OFF,
        WAIT_MENU,
        WAIT_WORLD,
        STARTED
    }

    static Config config;
    private static ExportJob.Settings pending;
    private static Auto auto = Auto.OFF;
    private static int autoTicks;

    private ClientSetup() {}

    static void init() {
        config = Config.load(FMLPaths.CONFIGDIR.get().resolve("packextract.properties").toFile());
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(ClientSetup::onScreenOpening);
        if (config.autoExport) {
            PackExtractMod.LOG.info("Pack Extract will export automatically once a world has loaded");
        }
    }

    static ExportJob.Settings settings(boolean images) {
        String version = ModList.get().getModContainerById(PackExtractMod.MODID)
            .map(c -> c.getModInfo().getVersion().toString()).orElse("?");
        File gameDir = Minecraft.getInstance().gameDirectory;
        return config.settings(gameDir, images, "Pack Extract " + version);
    }

    static ExportJob job(ExportJob.Settings s) {
        return new ExportJob(new Platform1201(s.viewerItems), s);
    }

    private static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("packextract")
            .executes(ctx -> start(ctx.getSource(), true))
            .then(Commands.literal("noimages").executes(ctx -> start(ctx.getSource(), false))));
    }

    private static int start(net.minecraft.commands.CommandSourceStack source, boolean images) {
        source.sendSystemMessage(Component.literal("[Pack Extract] Starting the export..."));
        // Opened on the next tick, once the chat screen has closed.
        pending = settings(images);
        return 1;
    }

    private static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (auto == Auto.WAIT_MENU) {
            tickAutoWorld(mc);
        } else if (auto == Auto.WAIT_WORLD) {
            tickAutoExport(mc);
        }
        if (pending != null && mc.screen == null && mc.level != null) {
            ExportJob.Settings s = pending;
            pending = null;
            mc.setScreen(new ExportScreen(job(s), false));
        }
    }

    private static void onScreenOpening(ScreenEvent.Opening event) {
        if (config.autoExport && auto == Auto.OFF && event.getNewScreen() instanceof TitleScreen) {
            auto = Auto.WAIT_MENU;
            autoTicks = 0;
        }
    }

    /** Opens (or creates) a flat creative world once the title screen has been up for a second. */
    private static void tickAutoWorld(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen) || ++autoTicks < 20) {
            return;
        }
        auto = Auto.WAIT_WORLD;
        autoTicks = 0;
        PackExtractMod.LOG.info("Automatic export: loading world {}", AUTO_WORLD);
        try {
            if (mc.getLevelSource().levelExists(AUTO_WORLD)) {
                mc.createWorldOpenFlows().loadLevel(mc.screen, AUTO_WORLD);
            } else {
                LevelSettings level = new LevelSettings(AUTO_WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel(AUTO_WORLD, level, new WorldOptions(0L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        } catch (Throwable t) {
            PackExtractMod.LOG.error("Automatic export: could not open a world", t);
            auto = Auto.STARTED;
        }
    }

    /** Waits for the world (and JEI, when installed), then starts the export. */
    private static void tickAutoExport(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        autoTicks++;
        boolean jeiReady = !ModList.get().isLoaded("jei") || JeiItems.ready();
        if (autoTicks < 100 || (!jeiReady && autoTicks < 1200)) {
            return;
        }
        if (mc.screen != null) {
            mc.setScreen(null);
        }
        auto = Auto.STARTED;
        PackExtractMod.LOG.info("Automatic export: starting (JEI {})", jeiReady ? "ready" : "not ready");
        mc.setScreen(new ExportScreen(job(settings(true)), config.quitAfterAutoExport));
    }
}
