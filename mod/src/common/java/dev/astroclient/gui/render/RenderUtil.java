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
        shape(context, px, py, pw, ph, r, color);
        Gfx.pop(context);
    }

    /** Rounded or square fill in framebuffer pixels (the matrix already undoes the GUI scale). */
    private static void shape(DrawContext context, int px, int py, int pw, int ph, float r, int color) {
        if (pw <= 0 || ph <= 0 || (color >>> 24) == 0) return;
        r = Math.min(r, Math.min(pw, ph) / 2f);
        if (r < 0.75f) solid(context, px, py, pw, ph, color);
        else if (!Gfx.roundedRect(context, px, py, pw, ph, Math.round(r), color)) rowCorners(context, px, py, pw, ph, r, color);
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

    /**
     * Nebula glass surface (Rounded corners): a hairline ring in the border colour, the fill inside it and
     * a faint light edge along the top. Sharp corners fall back to {@link #panel}.
     */
    public static void surface(DrawContext context, float x, float y, float w, float h, float radius, int fill, int border) {
        surface(context, x, y, w, h, radius, fill, border, 0.5f);
    }

    /** @param ring border thickness in GUI px (0.5 = hairline) */
    public static void surface(DrawContext context, float x, float y, float w, float h, float radius, int fill, int border, float ring) {
        if (ThemeSettings.get().sharp()) {
            panel(context, x, y, w, h, radius, fill, border);
            return;
        }
        if (w <= 0 || h <= 0) return;
        float scale = Gfx.guiScale();
        int px = Math.round(x * scale), py = Math.round(y * scale);
        int pw = Math.round(w * scale), ph = Math.round(h * scale);
        int t = Math.max(1, Math.round(ring * scale));
        float r = Math.min(radius * scale, Math.min(pw, ph) / 2f);
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        shape(context, px, py, pw, ph, r, border);
        shape(context, px + t, py + t, pw - 2 * t, ph - 2 * t, Math.max(0f, r - t), fill);
        // light catching the top edge, kept clear of the corners
        int inset = Math.round(r) + t;
        int light = ((fill >>> 24) * 0x1C / 255) << 24 | 0xFFFFFF;
        if (pw - 2 * inset > 0 && (light >>> 24) > 2) solid(context, px + inset, py + t, pw - 2 * inset, hairline(scale), light);
        Gfx.pop(context);
    }

    /**
     * Rounded rect with a vertical gradient. Built from two flat end caps and one gradient quad, so the
     * colours should be (nearly) opaque: translucent ones blend twice where the pieces overlap.
     */
    public static void gradientRect(DrawContext context, float x, float y, float w, float h, float radius, int top, int bottom) {
        if (w <= 0 || h <= 0 || ((top >>> 24) == 0 && (bottom >>> 24) == 0)) return;
        if (ThemeSettings.get().sharp()) radius = 0f;
        float scale = Gfx.guiScale();
        int px = Math.round(x * scale), py = Math.round(y * scale);
        int pw = Math.round(w * scale), ph = Math.round(h * scale);
        int r = Math.round(Math.min(radius * scale, Math.min(pw, ph) / 2f));
        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        if (r < 1) {
            context.fillGradient(px, py, px + pw, py + ph, top, bottom);
        } else {
            shape(context, px, py, pw, 2 * r, r, top);
            shape(context, px, py + ph - 2 * r, pw, 2 * r, r, bottom);
            if (ph > 2 * r) context.fillGradient(px, py + r, px + pw, py + ph - r, top, bottom);
        }
        Gfx.pop(context);
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
     * the spread; the "Accent glow" option scales its strength). Sharp style: a crisp outline just outside
     * the shape, at the glow colour's strength.
     */
    public static void glow(DrawContext context, float x, float y, float w, float h, float radius, int color, int spread) {
        ThemeSettings settings = ThemeSettings.get();
        ThemeSettings.Quality q = settings.effects;
        if (spread <= 0) return;
        if (settings.sharp()) {
            float strength = Math.min(1f, (color >>> 24) / 255f * 2f);
            outline(context, x - 1, y - 1, w + 2, h + 2, Theme.withAlpha(color | 0xFF000000, strength));
            return;
        }
        if (q == ThemeSettings.Quality.OFF || settings.glow <= 0) return;
        if (settings.glow != 100) color = (Math.min(255, (color >>> 24) * settings.glow / 100) << 24) | (color & 0x00FFFFFF);
        if ((color >>> 24) == 0) return;
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

    /**
     * The Nebula accent line: violet to cyan, fading out at both ends, with a highlight that drifts along
     * it every few seconds (Effects quality Off keeps it still). For menus, not the HUD.
     */
    public static void shimmerLine(DrawContext context, int x1, int y, int x2, int thickness, float alpha) {
        int width = x2 - x1;
        if (width <= 0 || alpha <= 0.01f) return;
        ThemeSettings.Quality q = ThemeSettings.get().effects;
        int slices = Math.max(1, Math.min(width, q == ThemeSettings.Quality.HIGH ? 48 : 16));
        float head = q == ThemeSettings.Quality.OFF ? -1f : (Animation.clock() * 0.16f) % 1.8f - 0.4f;
        for (int i = 0; i < slices; i++) {
            float f = (i + 0.5f) / slices;
            float edge = Math.min(1f, Math.min(f, 1f - f) * 5f);
            float d = (f - head) / 0.14f;
            float spark = head < -0.5f ? 0f : Math.max(0f, 1f - d * d);
            int color = Theme.lerp(Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, f), 0xFFFFFFFF, spark * 0.75f);
            int sx1 = x1 + width * i / slices;
            int sx2 = x1 + width * (i + 1) / slices;
            context.fill(sx1, y, sx2, y + thickness, Theme.withAlpha(color, alpha * edge * (0.5f + 0.5f * spark)));
        }
    }

    /**
     * Vertical 1px line that fades out towards both ends (two gradient quads).
     */
    public static void fadeLineVertical(DrawContext context, int x, int y1, int y2, int color) {
        int mid = (y1 + y2) / 2;
        int clear = color & 0x00FFFFFF;
        context.fillGradient(x, y1, x + 1, mid, clear, color);
        context.fillGradient(x, mid, x + 1, y2, color, clear);
    }
}
