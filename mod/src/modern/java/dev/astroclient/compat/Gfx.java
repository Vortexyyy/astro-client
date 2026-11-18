package dev.astroclient.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/** Version shim (1.21.9+): the GUI uses a 2D matrix stack and render pipelines. */
public final class Gfx {
    private Gfx() {}

    public static void push(DrawContext c) {
        c.getMatrices().pushMatrix();
    }

    public static void pop(DrawContext c) {
        c.getMatrices().popMatrix();
    }

    public static void translate(DrawContext c, float x, float y) {
        c.getMatrices().translate(x, y);
    }

    public static void scale(DrawContext c, float x, float y) {
        c.getMatrices().scale(x, y);
    }

    /** 1.21.6+ already batches all GUI drawing per frame. */
    public static void batch(DrawContext c, Runnable drawing) {
        drawing.run();
    }

    /** Rounded rect from cached corner masks, in framebuffer pixels. Returns false where unsupported. */
    public static boolean roundedRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        ShapeAtlas.roundedRect(c, x, y, w, h, r, color);
        return true;
    }

    /** 9-slice glow (pad pixels, built from rings like the stacked-rect glow). Returns false where unsupported. */
    public static boolean glow(DrawContext c, int x, int y, int w, int h, int r, int pad, int rings, int color) {
        ShapeAtlas.glow(c, x, y, w, h, r, pad, rings, color);
        return true;
    }

    public static float guiScale() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    /** Draws the region (u, v, w, h) of a texW×texH texture at (x, y), one texel per GUI pixel. */
    public static void texture(DrawContext c, Identifier id, int x, int y, float u, float v, int w, int h, int texW, int texH) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH);
    }

    /** Same, multiplied by an ARGB tint. */
    public static void texture(DrawContext c, Identifier id, int x, int y, float u, float v, int w, int h, int texW, int texH, int argb) {
        c.drawTexture(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, texW, texH, argb);
    }
}
