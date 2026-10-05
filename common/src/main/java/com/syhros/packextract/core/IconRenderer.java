package com.syhros.packextract.core;

/**
 * Draws item and fluid icons off screen. Pixels come back as RGBA bytes, bottom row first (as glReadPixels returns
 * them). Must be used on the render thread.
 */
public interface IconRenderer {

    int size();

    /** True when drawing goes to an off-screen buffer (otherwise into the corner of the screen). */
    boolean offscreen();

    byte[] renderItem(Object stack);

    /** Null when the fluid has no texture. */
    byte[] renderFluid(Object fluid);

    void delete();
}
