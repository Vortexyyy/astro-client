package dev.astroclient.gui.render;

import dev.astroclient.compat.Gfx;
import net.minecraft.client.gui.DrawContext;

/**
 * Shape helpers on top of {@link DrawContext#fill}. Vanilla 1.21.1 has no rounded rectangles,
 * so corners are drawn as one row of quads per physical pixel with an anti-aliased edge pixel.
 * Drawing happens in framebuffer pixels (undoing the GUI scale) so corners stay smooth at any
 * GUI scale. Matrix calls go through {@link Gfx}, which differs per Minecraft version.
 */
public final class RenderUtil {
    private RenderUtil() {}

    public static void roundedRect(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        float scale = Gfx.guiScale();
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);

        int px = Math.round(x * scale);
        int py = Math.round(y * scale);
        int pw = Math.round(w * scale);
        int ph = Math.round(h * scale);
        float r = Math.min(radius * scale, Math.min(pw, ph) / 2f);
        int cornerRows = (int) Math.ceil(r);

        // straight middle section
        if (ph - 2 * cornerRows > 0) context.fill(px, py + cornerRows, px + pw, py + ph - cornerRows, color);

        for (int row = 0; row < cornerRows; row++) {
            float dy = r - row - 0.5f;
            float inset = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            int solid = (int) Math.ceil(inset);
            int edgeAlpha = Math.round((color >>> 24) * (solid - inset));
            int edge = (edgeAlpha << 24) | (color & 0x00FFFFFF);

            for (int yy : new int[] {py + row, py + ph - 1 - row}) {
                context.fill(px + solid, yy, px + pw - solid, yy + 1, color);
                if (edgeAlpha > 0 && solid > 0) {
                    context.fill(px + solid - 1, yy, px + solid, yy + 1, edge);
                    context.fill(px + pw - solid, yy, px + pw - solid + 1, yy + 1, edge);
                }
            }
        }
        Gfx.pop(context);
    }

    public static void circle(DrawContext context, float cx, float cy, float radius, int color) {
        roundedRect(context, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Soft neon glow: stacked, increasingly transparent rounded rects around the shape. */
    public static void glow(DrawContext context, float x, float y, float w, float h, float radius, int color, int spread) {
        int baseAlpha = color >>> 24;
        for (int i = spread; i >= 1; i--) {
            float t = 1f - (float) i / (spread + 1);
            int a = Math.round(baseAlpha * t * t / spread * 2f);
            if (a <= 0) continue;
            roundedRect(context, x - i, y - i, w + i * 2, h + i * 2, radius + i, (a << 24) | (color & 0x00FFFFFF));
        }
    }

    /** Horizontal gradient bar made of vertical slices (vanilla only has vertical gradients). */
    public static void horizontalGradient(DrawContext context, int x1, int y1, int x2, int y2, int left, int right) {
        int width = x2 - x1;
        int slices = Math.max(1, Math.min(width, 32));
        for (int i = 0; i < slices; i++) {
            int sx1 = x1 + width * i / slices;
            int sx2 = x1 + width * (i + 1) / slices;
            context.fill(sx1, y1, sx2, y2, Theme.lerp(left, right, (i + 0.5f) / slices));
        }
    }
}
