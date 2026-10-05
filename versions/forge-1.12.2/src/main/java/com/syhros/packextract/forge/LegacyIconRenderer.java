package com.syhros.packextract.forge;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.syhros.packextract.core.IconRenderer;

/**
 * Renders item and fluid icons into an off-screen framebuffer with the game's own GUI item renderer and reads the
 * pixels back. State changes go through GlStateManager, which caches GL state on 1.12.
 */
final class LegacyIconRenderer implements IconRenderer {

    private final Minecraft mc = Minecraft.getMinecraft();
    private final int size;
    private final Framebuffer fb;
    private final ByteBuffer pixels;
    private final byte[] bytes;

    LegacyIconRenderer(int size) {
        this.size = size;
        this.fb = OpenGlHelper.isFramebufferEnabled() ? new Framebuffer(size, size, true) : null;
        if (fb != null) {
            fb.setFramebufferColor(0, 0, 0, 0);
        }
        this.pixels = BufferUtils.createByteBuffer(size * size * 4);
        this.bytes = new byte[size * size * 4];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean offscreen() {
        return fb != null;
    }

    @Override
    public byte[] renderItem(Object item) {
        ItemStack stack = (ItemStack) item;
        begin();
        try {
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableDepth();
            RenderItem renderItem = mc.getRenderItem();
            renderItem.zLevel = 0;
            renderItem.renderItemAndEffectIntoGUI(stack, 0, 0);
        } finally {
            end();
        }
        return bytes;
    }

    @Override
    public byte[] renderFluid(Object fluidObject) {
        Fluid fluid = (Fluid) fluidObject;
        FluidStack stack = new FluidStack(fluid, 1000);
        ResourceLocation still = fluid.getStill(stack);
        if (still == null) {
            return null;
        }
        TextureAtlasSprite sprite = mc.getTextureMapBlocks().getAtlasSprite(still.toString());
        begin();
        try {
            mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
            int c = fluid.getColor(stack);
            GlStateManager.color(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, 1f);
            GlStateManager.disableLighting();
            Tessellator t = Tessellator.getInstance();
            BufferBuilder b = t.getBuffer();
            b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
            b.pos(0, 16, 0).tex(sprite.getMinU(), sprite.getMaxV()).endVertex();
            b.pos(16, 16, 0).tex(sprite.getMaxU(), sprite.getMaxV()).endVertex();
            b.pos(16, 0, 0).tex(sprite.getMaxU(), sprite.getMinV()).endVertex();
            b.pos(0, 0, 0).tex(sprite.getMinU(), sprite.getMinV()).endVertex();
            t.draw();
            GlStateManager.color(1, 1, 1, 1);
        } finally {
            end();
        }
        return bytes;
    }

    private void begin() {
        GlStateManager.pushAttrib();
        if (fb != null) {
            fb.framebufferClear();
            fb.bindFramebuffer(true);
        } else {
            GlStateManager.viewport(0, 0, size, size);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(0, 0, size, size);
            GlStateManager.clearColor(0, 0, 0, 0);
            GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        }
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 16, 16, 0, 1000, 3000);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.translate(0, 0, -2000);
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        // Keep the background transparent: alpha adds up instead of being multiplied.
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
            GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.004f);
        GlStateManager.color(1, 1, 1, 1);
        // Inventory screens draw items at full brightness.
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
    }

    private void end() {
        GL11.glFinish();
        pixels.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.get(bytes);
        resetLeakedState();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        if (fb != null) {
            mc.getFramebuffer().bindFramebuffer(true);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GlStateManager.viewport(0, 0, mc.displayWidth, mc.displayHeight);
        }
        GlStateManager.popAttrib();
    }

    /**
     * Some mods' item renderers leave state behind (a bound shader, a changed texture matrix), especially when they
     * throw half-way. Reset them after each icon so the next one is not drawn wrong.
     */
    private void resetLeakedState() {
        try {
            OpenGlHelper.glUseProgram(0);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.disableTexture2D();
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.enableTexture2D();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        } catch (Throwable ignored) {
            // best effort
        }
    }

    @Override
    public void delete() {
        if (fb != null) {
            fb.deleteFramebuffer();
        }
    }
}
