package dev.astroclient.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Version shim (1.21.1): GUI matrix ops and textured quads. */
public final class Gfx {
    private Gfx() {}

    public static void push(DrawContext c) {
        c.getMatrices().push();
    }

    public static void pop(DrawContext c) {
        c.getMatrices().pop();
    }

    public static void translate(DrawContext c, float x, float y) {
        c.getMatrices().translate(x, y, 0f);
    }

    public static void scale(DrawContext c, float x, float y) {
        c.getMatrices().scale(x, y, 1f);
    }

    /**
     * Runs GUI drawing as one batch. On 1.21.1 every fill/text call is otherwise its own GPU draw
     * call, which costs real FPS for HUD elements drawn every frame. Don't use around scissor regions.
     */
    public static void batch(DrawContext c, Runnable drawing) {
        c.draw(drawing);
    }

    public static float guiScale() {
        return (float) MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    /** Draws the region (u, v, w, h) of a texW×texH texture at (x, y), one texel per GUI pixel. */
    public static void texture(DrawContext c, Identifier id, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        c.drawTexture(id, x, y, u, v, w, h, texW, texH);
    }
}
