package com.syhros.packextract.forge;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

import com.syhros.packextract.core.ExportJob;

/** Shows progress and drives the export a slice per frame. */
public class ExportScreen extends GuiScreen {

    private static final int OPEN_FOLDER = 1;
    private static final int CLOSE = 2;

    private final ExportJob job;
    private final boolean quitWhenDone;
    private int frames;
    private boolean buttonsShown;
    private boolean quitting;

    public ExportScreen(ExportJob job, boolean quitWhenDone) {
        this.job = job;
        this.quitWhenDone = quitWhenDone;
    }

    @Override
    public boolean doesGuiPauseGame() {
        return true;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        buttonsShown = false;
        if (job.finished()) {
            showButtons();
        }
    }

    private void showButtons() {
        buttonsShown = true;
        buttonList.add(new GuiButton(OPEN_FOLDER, width / 2 - 154, height - 40, 150, 20, "Open folder"));
        buttonList.add(new GuiButton(CLOSE, width / 2 + 4, height - 40, 150, 20, "Done"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Let the screen draw once before the first (slow) step.
        if (++frames > 2 && !job.finished()) {
            job.step(PackExtractMod.config.frameBudgetMillis);
        }
        if (job.finished() && !buttonsShown) {
            showButtons();
            if (quitWhenDone && !quitting) {
                quitting = true;
                PackExtractMod.LOG.info(job.failed() ? "Export failed, closing the game" : "Export done, closing the game");
                mc.shutdown();
            }
        }

        drawDefaultBackground();
        int y = height / 4;
        drawCenteredString(fontRenderer, "Pack Extract", width / 2, y, 0xFFFFFF);
        y += 20;
        if (!job.finished()) {
            drawCenteredString(fontRenderer, "Step " + job.stageNumber() + ": " + job.status(), width / 2, y, 0xDDDDDD);
            y += 14;
            float p = job.stageProgress();
            if (p >= 0) {
                int barWidth = Math.min(300, width - 40), left = width / 2 - barWidth / 2;
                drawRect(left, y, left + barWidth, y + 6, 0xFF333333);
                drawRect(left, y, left + Math.round(barWidth * p), y + 6, 0xFF55CC55);
                y += 14;
            }
            drawCenteredString(fontRenderer, "Writing to " + shortPath(job.dir()), width / 2, y, 0x999999);
            y += 12;
            drawCenteredString(fontRenderer, "Press Esc to cancel", width / 2, y, 0x777777);
        } else if (job.failed()) {
            drawCenteredString(fontRenderer, "Export stopped: " + job.failure(), width / 2, y, 0xFF5555);
            y += 14;
            drawCenteredString(fontRenderer, "Details in errors.log in " + shortPath(job.dir()), width / 2, y, 0xAAAAAA);
        } else {
            drawCenteredString(fontRenderer, "Export finished", width / 2, y, 0x55FF55);
            y += 16;
            Map<String, Number> c = job.counts();
            line(y, "Items: " + c.get("items") + ", fluids: " + c.get("fluids"));
            y += 12;
            line(y, "Recipes: " + c.get("recipes") + " (GregTech " + c.getOrDefault("gregtechRecipes", 0) + ")");
            y += 12;
            if (c.get("imagesWritten") != null) {
                line(y, "Images: " + c.get("imagesWritten") + " (" + c.get("blankImages") + " blank)");
                y += 12;
            }
            line(y, "Problems logged: " + job.problems().errors() + " (see errors.log)");
            y += 16;
            drawCenteredString(fontRenderer, shortPath(job.dir()), width / 2, y, 0xAAAAAA);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void line(int y, String text) {
        drawCenteredString(fontRenderer, text, width / 2, y, 0xDDDDDD);
    }

    private static String shortPath(File f) {
        String s = f.getAbsolutePath();
        return s.length() > 70 ? "..." + s.substring(s.length() - 67) : s;
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        if (key == Keyboard.KEY_ESCAPE) {
            if (!job.finished()) {
                job.cancel();
            } else {
                mc.displayGuiScreen(null);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == OPEN_FOLDER) {
            try {
                java.awt.Desktop.getDesktop().open(job.dir());
            } catch (Throwable t) {
                Sys.openURL("file://" + job.dir().getAbsolutePath());
            }
        } else if (button.id == CLOSE) {
            mc.displayGuiScreen(null);
        }
    }
}
