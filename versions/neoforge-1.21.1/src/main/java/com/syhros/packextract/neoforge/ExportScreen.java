package com.syhros.packextract.neoforge;

import java.io.File;
import java.util.Map;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.lwjgl.glfw.GLFW;

import com.syhros.packextract.core.ExportJob;

/** Shows progress and drives the export a slice per frame. */
final class ExportScreen extends Screen {

    private final ExportJob job;
    private final boolean quitWhenDone;
    private int frames;
    private boolean buttonsShown;
    private boolean quitting;

    ExportScreen(ExportJob job, boolean quitWhenDone) {
        super(Component.literal("Pack Extract"));
        this.job = job;
        this.quitWhenDone = quitWhenDone;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void init() {
        clearWidgets();
        buttonsShown = false;
        if (job.finished()) {
            showButtons();
        }
    }

    private void showButtons() {
        buttonsShown = true;
        addRenderableWidget(Button.builder(Component.literal("Open folder"), b -> Util.getPlatform().openFile(job.dir()))
            .bounds(width / 2 - 154, height - 40, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(width / 2 + 4, height - 40, 150, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Export first, before this screen queues anything to draw. Let the screen draw once before the first
        // (slow) step.
        if (++frames > 2 && !job.finished()) {
            job.step(ClientSetup.config.frameBudgetMillis);
        }
        if (job.finished() && !buttonsShown) {
            showButtons();
            if (quitWhenDone && !quitting) {
                quitting = true;
                PackExtractMod.LOG.info(job.failed() ? "Export failed, closing the game" : "Export done, closing the game");
                minecraft.stop();
            }
        }

        // Background and buttons first (1.20.2+ draws the background in super.render), then the text on top.
        super.render(g, mouseX, mouseY, partialTick);
        int y = height / 4;
        g.drawCenteredString(font, "Pack Extract", width / 2, y, 0xFFFFFF);
        y += 20;
        if (!job.finished()) {
            g.drawCenteredString(font, "Step " + job.stageNumber() + ": " + job.status(), width / 2, y, 0xDDDDDD);
            y += 14;
            float p = job.stageProgress();
            if (p >= 0) {
                int barWidth = Math.min(300, width - 40), left = width / 2 - barWidth / 2;
                g.fill(left, y, left + barWidth, y + 6, 0xFF333333);
                g.fill(left, y, left + Math.round(barWidth * p), y + 6, 0xFF55CC55);
                y += 14;
            }
            g.drawCenteredString(font, "Writing to " + shortPath(job.dir()), width / 2, y, 0x999999);
            y += 12;
            g.drawCenteredString(font, "Press Esc to cancel", width / 2, y, 0x777777);
        } else if (job.failed()) {
            g.drawCenteredString(font, "Export stopped: " + job.failure(), width / 2, y, 0xFF5555);
            y += 14;
            g.drawCenteredString(font, "Details in errors.log in " + shortPath(job.dir()), width / 2, y, 0xAAAAAA);
        } else {
            g.drawCenteredString(font, "Export finished", width / 2, y, 0x55FF55);
            y += 16;
            Map<String, Number> c = job.counts();
            g.drawCenteredString(font, "Items: " + c.get("items") + ", fluids: " + c.get("fluids"), width / 2, y,
                0xDDDDDD);
            y += 12;
            g.drawCenteredString(font, "Recipes: " + c.get("recipes") + " (GregTech " + c.getOrDefault("gregtechRecipes", 0)
                + ")", width / 2, y, 0xDDDDDD);
            y += 12;
            if (c.get("imagesWritten") != null) {
                g.drawCenteredString(font, "Images: " + c.get("imagesWritten") + " (" + c.get("blankImages") + " blank)",
                    width / 2, y, 0xDDDDDD);
                y += 12;
            }
            g.drawCenteredString(font, "Problems logged: " + job.problems().errors() + " (see errors.log)", width / 2, y,
                0xDDDDDD);
            y += 16;
            g.drawCenteredString(font, shortPath(job.dir()), width / 2, y, 0xAAAAAA);
        }
    }

    private static String shortPath(File f) {
        String s = f.getAbsolutePath();
        return s.length() > 70 ? "..." + s.substring(s.length() - 67) : s;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (!job.finished()) {
                job.cancel();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
