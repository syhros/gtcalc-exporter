package com.syhros.packextract.neoforge;

import java.nio.ByteBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.syhros.packextract.core.IconRenderer;

/**
 * Draws icons with the game's own GUI item renderer into an off-screen target (16 GUI pixels = {@code size} real
 * pixels) and reads them back. Uses the same projection the game sets up for screens.
 */
final class ModernIconRenderer implements IconRenderer {

    private final Minecraft mc = Minecraft.getInstance();
    private final int size;
    private final TextureTarget target;
    private final ByteBuffer pixels;
    private final byte[] bytes;
    private Matrix4f savedProjection;
    private VertexSorting savedSorting;

    ModernIconRenderer(int size) {
        this.size = size;
        this.target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        target.setClearColor(0, 0, 0, 0);
        this.pixels = BufferUtils.createByteBuffer(size * size * 4);
        this.bytes = new byte[size * size * 4];
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean offscreen() {
        return true;
    }

    @Override
    public byte[] renderItem(Object item) {
        ItemStack stack = (ItemStack) item;
        begin();
        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            g.renderItem(stack, 0, 0);
            g.flush();
        } finally {
            end();
        }
        return bytes;
    }

    @Override
    public byte[] renderFluid(Object fluidObject) {
        Fluid fluid = (Fluid) fluidObject;
        FluidStack stack = new FluidStack(fluid, 1000);
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation still = ext.getStillTexture(stack);
        if (still == null) {
            return null;
        }
        TextureAtlasSprite sprite = mc.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(still);
        int c = ext.getTintColor(stack);
        begin();
        try {
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            float a = ((c >> 24) & 0xFF) / 255f;
            g.setColor(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f, a == 0 ? 1 : a);
            g.blit(0, 0, 0, 16, 16, sprite);
            g.setColor(1, 1, 1, 1);
            g.flush();
        } finally {
            end();
        }
        return bytes;
    }

    private void begin() {
        // Anything the current screen has queued must not land in the icon.
        mc.renderBuffers().bufferSource().endBatch();
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        savedProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        savedSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, 1000, 21000),
            VertexSorting.ORTHOGRAPHIC_Z);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.translation(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void end() {
        GL11.glFinish();
        pixels.clear();
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glReadPixels(0, 0, size, size, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels.get(bytes);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(savedProjection, savedSorting);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        mc.getMainRenderTarget().bindWrite(true);
    }

    @Override
    public void delete() {
        target.destroyBuffers();
    }
}
