package com.syhros.packextract.export;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

/**
 * Renders item and fluid icons into an off-screen framebuffer and reads the pixels back. Must run on the render
 * thread.
 */
public final class IconRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final RenderItem renderItem = new RenderItem();
    private final int size;
    private final Framebuffer fb;
    private final ByteBuffer pixels;
    private final byte[] bytes;

    public IconRenderer(int size) {
        this.size = size;
        this.fb = OpenGlHelper.isFramebufferEnabled() ? new Framebuffer(size, size, true) : null;
        if (fb != null) {
            fb.setFramebufferColor(0, 0, 0, 0);
        }
        this.pixels = BufferUtils.createByteBuffer(size * size * 4);
        this.bytes = new byte[size * size * 4];
    }

    public int size() {
        return size;
    }

    /** True when rendering goes to an off-screen buffer (otherwise into the corner of the screen). */
    public boolean offscreen() {
        return fb != null;
    }

    /** Renders an item and returns its RGBA pixels (bottom row first). */
    public byte[] renderItem(ItemStack stack) {
        begin();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            renderItem.zLevel = 0;
            renderItem.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), stack, 0, 0);
        } finally {
            end();
        }
        return bytes;
    }

    /** Renders a fluid's still texture, tinted with its colour, and returns its RGBA pixels. */
    public byte[] renderFluid(Fluid fluid) {
        IIcon icon = fluid.getStillIcon();
        if (icon == null) {
            icon = fluid.getIcon();
        }
        if (icon == null) {
            return null;
        }
        begin();
        try {
            // Forge fluid icons are registered on the blocks atlas.
            mc.getTextureManager().bindTexture(TextureMap.locationBlocksTexture);
            int c = fluid.getColor();
            GL11.glColor4f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
            GL11.glDisable(GL11.GL_LIGHTING);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.addVertexWithUV(0, 16, 0, icon.getMinU(), icon.getMaxV());
            t.addVertexWithUV(16, 16, 0, icon.getMaxU(), icon.getMaxV());
            t.addVertexWithUV(16, 0, 0, icon.getMaxU(), icon.getMinV());
            t.addVertexWithUV(0, 0, 0, icon.getMinU(), icon.getMinV());
            t.draw();
            GL11.glColor4f(1, 1, 1, 1);
        } finally {
            end();
        }
        return bytes;
    }

    private void begin() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        if (fb != null) {
            fb.framebufferClear();
            fb.bindFramebuffer(true);
        } else {
            GL11.glViewport(0, 0, size, size);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, size, size);
            GL11.glClearColor(0, 0, 0, 0);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 16, 16, 0, 1000, 3000);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glTranslatef(0, 0, -2000);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        // Keep the background transparent: alpha adds up instead of being multiplied.
        OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004f);
        GL11.glColor4f(1, 1, 1, 1);
    }

    private void end() {
        GL11.glFinish();
        pixels.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.get(bytes);
        RenderHelper.disableStandardItemLighting();
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        if (fb != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        GL11.glPopAttrib();
    }

    public void delete() {
        if (fb != null) {
            fb.deleteFramebuffer();
        }
    }
}
