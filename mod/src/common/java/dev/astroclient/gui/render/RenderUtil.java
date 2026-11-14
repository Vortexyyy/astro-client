package dev.astroclient.gui.render;

import dev.astroclient.compat.Gfx;
import dev.astroclient.gui.theme.ThemeSettings;
import net.minecraft.client.gui.DrawContext;

/**
 * Shape helpers. Drawing happens in framebuffer pixels (undoing the GUI scale) so edges stay crisp at
 * any GUI scale. On 1.21.9+ shapes come from a cached alpha-mask atlas ({@link Gfx#roundedRect}: a few
 * quads per shape, all batched together); on 1.21.1 corners fall back to one fill per pixel row.
 * With the Sharp corner style (the Orbit default) every shape is square.
 */
public final class RenderUtil {
    private RenderUtil() {}

    public static void roundedRect(DrawContext context, float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        if (ThemeSettings.get().sharp()) radius = 0f;
        float scale = Gfx.guiScale();
        int px = Math.round(x * scale);
        int py = Math.round(y * scale);
        int pw = Math.round(w * scale);
        int ph = Math.round(h * scale);
        float r = Math.min(radius * scale, Math.min(pw, ph) / 2f);
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        if (r < 0.75f) solid(context, px, py, pw, ph, color);
        else if (!Gfx.roundedRect(context, px, py, pw, ph, Math.round(r), color)) rowCorners(context, px, py, pw, ph, r, color);
        Gfx.pop(context);
    }

    /** 1.21.1 fallback: one quad per pixel row of each corner, with an anti-aliased edge pixel. */
    private static void rowCorners(DrawContext context, int px, int py, int pw, int ph, float r, int color) {
        int cornerRows = (int) Math.ceil(r);
        if (ph - 2 * cornerRows > 0) context.fill(px, py + cornerRows, px + pw, py + ph - cornerRows, color);
        int alpha = color >>> 24;
        int rgb = color & 0x00FFFFFF;
        for (int row = 0; row < cornerRows; row++) {
            float dy = r - row - 0.5f;
            float inset = r - (float) Math.sqrt(Math.max(0f, r * r - dy * dy));
            int solid = (int) Math.ceil(inset);
            int edgeAlpha = Math.round(alpha * (solid - inset));
            int edge = (edgeAlpha << 24) | rgb;
            int top = py + row;
            int bottom = py + ph - 1 - row;
            context.fill(px + solid, top, px + pw - solid, top + 1, color);
            context.fill(px + solid, bottom, px + pw - solid, bottom + 1, color);
            if (edgeAlpha > 0 && solid > 0) {
                context.fill(px + solid - 1, top, px + solid, top + 1, edge);
                context.fill(px + pw - solid, top, px + pw - solid + 1, top + 1, edge);
                context.fill(px + solid - 1, bottom, px + solid, bottom + 1, edge);
                context.fill(px + pw - solid, bottom, px + pw - solid + 1, bottom + 1, edge);
            }
        }
    }

    /** Square fill in framebuffer pixels, through the shape atlas where available so it batches with shapes. */
    private static void solid(DrawContext context, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        if (!Gfx.roundedRect(context, x, y, w, h, 0, color)) context.fill(x, y, x + w, y + h, color);
    }

    /** Border thickness in framebuffer pixels: a crisp hairline at every GUI scale. */
    private static int hairline(float scale) {
        return Math.max(1, Math.round(scale / 2f));
    }

    /**
     * Orbit surface: a fill with a hairline border inside the bounds. With Rounded corners it is a plain
     * rounded fill (the classic look).
     */
    public static void panel(DrawContext context, float x, float y, float w, float h, float radius, int fill, int border) {
        if (!ThemeSettings.get().sharp()) {
            roundedRect(context, x, y, w, h, radius, fill);
            return;
        }
        roundedRect(context, x, y, w, h, 0f, fill);
        outline(context, x, y, w, h, border);
    }

    /** Hairline rectangle outline inside the given bounds. */
    public static void outline(DrawContext context, float x, float y, float w, float h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        float scale = Gfx.guiScale();
        int px = Math.round(x * scale), py = Math.round(y * scale);
        int pw = Math.round(w * scale), ph = Math.round(h * scale);
        int t = Math.min(hairline(scale), Math.min(pw, ph) / 2);
        if (t <= 0) return;
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        solid(context, px, py, pw, t, color);
        solid(context, px, py + ph - t, pw, t, color);
        solid(context, px, py + t, t, ph - 2 * t, color);
        solid(context, px + pw - t, py + t, t, ph - 2 * t, color);
        Gfx.pop(context);
    }

    public static void circle(DrawContext context, float cx, float cy, float radius, int color) {
        roundedRect(context, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /**
     * Emphasis around a shape. Rounded style: a soft glow (Effects quality Off draws nothing, Low halves
     * the spread). Sharp style: a crisp outline just outside the shape, at the glow colour's strength.
     */
    public static void glow(DrawContext context, float x, float y, float w, float h, float radius, int color, int spread) {
        ThemeSettings.Quality q = ThemeSettings.get().effects;
        if (spread <= 0) return;
        if (ThemeSettings.get().sharp()) {
            float strength = Math.min(1f, (color >>> 24) / 255f * 2f);
            outline(context, x - 1, y - 1, w + 2, h + 2, Theme.withAlpha(color | 0xFF000000, strength));
            return;
        }
        if (q == ThemeSettings.Quality.OFF) return;
        if (q == ThemeSettings.Quality.LOW) spread = Math.max(1, spread / 2);
        float scale = Gfx.guiScale();
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        boolean done = Gfx.glow(context, Math.round(x * scale), Math.round(y * scale), Math.round(w * scale), Math.round(h * scale),
                Math.round(radius * scale), Math.round(spread * scale), spread, color);
        Gfx.pop(context);
        if (done) return;
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
        int slices = Math.max(1, Math.min(width, ThemeSettings.get().effects == ThemeSettings.Quality.HIGH ? 32 : 12));
        for (int i = 0; i < slices; i++) {
            int sx1 = x1 + width * i / slices;
            int sx2 = x1 + width * (i + 1) / slices;
            context.fill(sx1, y1, sx2, y2, Theme.lerp(left, right, (i + 0.5f) / slices));
        }
    }
}
