package dev.astroclient.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Rounded shapes from small cached alpha masks (1.21.9+, where textured GUI quads take a tint and are
 * batched with everything else). A rounded rect costs 3 fills + 4 corner quads instead of one quad per
 * pixel row, and a glow 9 quads instead of up to 8 stacked rounded rects. All coordinates are
 * framebuffer pixels; callers undo the GUI scale first.
 */
final class ShapeAtlas {
    /** Supersampling per axis when computing edge coverage (4 = 16 samples per pixel). */
    private static final int SAMPLES = 4;
    private static final int MAX_RADIUS = 256;

    private record Mask(Identifier id, int size, int centerAlpha) {}

    /** Radii up to this share one atlas texture, so every rounded rect and solid fill batches together. */
    private static final int ATLAS_MAX = 48;
    private static Identifier atlas;
    private static int atlasW, atlasH;
    private static final int[] ATLAS_X = new int[ATLAS_MAX + 1];
    private static int solidU, solidV;

    private static final Map<Integer, Mask> CORNERS = new HashMap<>();
    private static final Map<Long, Mask> GLOWS = new HashMap<>();

    private ShapeAtlas() {}

    /** Filled rounded rect with an anti-aliased edge; r is the corner radius in pixels (0 = square). */
    static void roundedRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(Math.min(r, MAX_RADIUS), Math.min(w, h) / 2));
        if (w <= 0 || h <= 0) return;
        if (r <= ATLAS_MAX) {
            atlasRect(c, x, y, w, h, r, color);
            return;
        }
        if (r <= 0) {
            c.fill(x, y, x + w, y + h, color);
            return;
        }
        Mask m = corner(r);
        // cross-shaped body, then the four corners as quadrants of one disc texture
        c.fill(x + r, y, x + w - r, y + h, color);
        if (h > 2 * r) {
            c.fill(x, y + r, x + r, y + h - r, color);
            c.fill(x + w - r, y + r, x + w, y + h - r, color);
        }
        int tex = m.size;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, x, y, 0, 0, r, r, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, x + w - r, y, r, 0, r, r, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, x, y + h - r, 0, r, r, r, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, x + w - r, y + h - r, r, r, r, r, tex, tex, color);
    }

    /**
     * Soft glow around a w×h rounded rect at (x, y) with corner radius r, reaching pad pixels outward,
     * built from {@code rings} quadratic-fade rings like the old stacked-rect glow.
     */
    static void glow(DrawContext c, int x, int y, int w, int h, int r, int pad, int rings, int color) {
        if (pad <= 0 || rings <= 0) return;
        r = Math.max(0, Math.min(Math.min(r, MAX_RADIUS), Math.min(w, h) / 2));
        Mask m = glowMask(r, pad, rings);
        int k = r + pad;   // corner tile size
        int tex = m.size;  // 2k + 1; the centre texel row/column is what gets stretched
        int ox = x - pad, oy = y - pad, ow = w + 2 * pad, oh = h + 2 * pad;
        int midW = ow - 2 * k, midH = oh - 2 * k;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox, oy, 0, 0, k, k, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox + ow - k, oy, k + 1, 0, k, k, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox, oy + oh - k, 0, k + 1, k, k, tex, tex, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox + ow - k, oy + oh - k, k + 1, k + 1, k, k, tex, tex, color);
        if (midW > 0) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox + k, oy, k, 0, midW, k, 1, k, tex, tex, color);
            c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox + k, oy + oh - k, k, k + 1, midW, k, 1, k, tex, tex, color);
        }
        if (midH > 0) {
            c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox, oy + k, 0, k, k, midH, k, 1, tex, tex, color);
            c.drawTexture(RenderPipelines.GUI_TEXTURED, m.id, ox + ow - k, oy + k, k + 1, k, k, midH, k, 1, tex, tex, color);
        }
        if (midW > 0 && midH > 0) {
            int a = (color >>> 24) * m.centerAlpha / 255;
            c.fill(ox + k, oy + k, ox + ow - k, oy + oh - k, (a << 24) | (color & 0xFFFFFF));
        }
    }

    /** Rounded rect drawn entirely from the shared atlas: body quads stretch one opaque texel. */
    private static void atlasRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        ensureAtlas();
        if (r == 0) {
            solid(c, x, y, w, h, color);
            return;
        }
        solid(c, x + r, y, w - 2 * r, h, color);
        if (h > 2 * r) {
            solid(c, x, y + r, r, h - 2 * r, color);
            solid(c, x + w - r, y + r, r, h - 2 * r, color);
        }
        int u = ATLAS_X[r];
        c.drawTexture(RenderPipelines.GUI_TEXTURED, atlas, x, y, u, 0, r, r, atlasW, atlasH, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, atlas, x + w - r, y, u + r, 0, r, r, atlasW, atlasH, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, atlas, x, y + h - r, u, r, r, r, atlasW, atlasH, color);
        c.drawTexture(RenderPipelines.GUI_TEXTURED, atlas, x + w - r, y + h - r, u + r, r, r, r, atlasW, atlasH, color);
    }

    private static void solid(DrawContext c, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, atlas, x, y, solidU, solidV, w, h, 1, 1, atlasW, atlasH, color);
    }

    /** One texture holding a disc for every radius 1..ATLAS_MAX side by side, 1 px apart, plus an opaque texel. */
    private static void ensureAtlas() {
        if (atlas != null) return;
        int x = 0;
        for (int r = 1; r <= ATLAS_MAX; r++) {
            ATLAS_X[r] = x;
            x += 2 * r + 1;
        }
        solidU = x;
        solidV = 0;
        atlasW = x + 2;
        atlasH = 2 * ATLAS_MAX;
        NativeImage img = new NativeImage(atlasW, atlasH, true);
        for (int r = 1; r <= ATLAS_MAX; r++) {
            int ox = ATLAS_X[r];
            float rr = r * (float) r;
            for (int py = 0; py < 2 * r; py++) {
                for (int px = 0; px < 2 * r; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < SAMPLES; sy++) {
                        for (int sx = 0; sx < SAMPLES; sx++) {
                            float dx = px + (sx + 0.5f) / SAMPLES - r;
                            float dy = py + (sy + 0.5f) / SAMPLES - r;
                            if (dx * dx + dy * dy <= rr) inside++;
                        }
                    }
                    img.setColorArgb(ox + px, py, ((inside * 255 / (SAMPLES * SAMPLES)) << 24) | 0xFFFFFF);
                }
            }
        }
        for (int py = 0; py < 2; py++) for (int px = 0; px < 2; px++) img.setColorArgb(solidU + px, solidV + py, 0xFFFFFFFF);
        atlas = register("shape/atlas", img);
    }

    private static Mask corner(int r) {
        return CORNERS.computeIfAbsent(r, key -> {
            int size = 2 * r;
            NativeImage img = new NativeImage(size, size, false);
            float rr = r * (float) r;
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    int inside = 0;
                    for (int sy = 0; sy < SAMPLES; sy++) {
                        for (int sx = 0; sx < SAMPLES; sx++) {
                            float dx = px + (sx + 0.5f) / SAMPLES - r;
                            float dy = py + (sy + 0.5f) / SAMPLES - r;
                            if (dx * dx + dy * dy <= rr) inside++;
                        }
                    }
                    int a = inside * 255 / (SAMPLES * SAMPLES);
                    img.setColorArgb(px, py, (a << 24) | 0xFFFFFF);
                }
            }
            return new Mask(register("shape/corner_" + r, img), size, 255);
        });
    }

    private static Mask glowMask(int r, int pad, int rings) {
        long key = ((long) r << 40) | ((long) pad << 20) | rings;
        return GLOWS.computeIfAbsent(key, k0 -> {
            int k = r + pad;
            int size = 2 * k + 1;
            float[] ringAlpha = new float[rings + 1];
            for (int i = 1; i <= rings; i++) {
                float t = 1f - (float) i / (rings + 1);
                ringAlpha[i] = Math.min(1f, t * t / rings * 2f);
            }
            NativeImage img = new NativeImage(size, size, false);
            int center = 0;
            for (int py = 0; py < size; py++) {
                for (int px = 0; px < size; px++) {
                    // signed distance to a core rounded rect (size 2r+1, radius r) centred in the tile
                    float qx = Math.abs(px - k) - 0.5f;
                    float qy = Math.abs(py - k) - 0.5f;
                    float ex = Math.max(qx, 0f), ey = Math.max(qy, 0f);
                    float d = (float) Math.sqrt(ex * ex + ey * ey) + Math.min(Math.max(qx, qy), 0f) - r;
                    // ring i covered everything up to i*pad/rings past the shape edge
                    float keep = 1f;
                    for (int i = 1; i <= rings; i++) {
                        if (d <= (float) i * pad / rings) keep *= 1f - ringAlpha[i];
                    }
                    int a = Math.round((1f - keep) * 255);
                    if (px == k && py == k) center = a;
                    img.setColorArgb(px, py, (a << 24) | 0xFFFFFF);
                }
            }
            return new Mask(register("shape/glow_" + r + "_" + pad + "_" + rings, img), size, center);
        });
    }

    private static Identifier register(String path, NativeImage img) {
        Identifier id = Identifier.of("astroclient", path);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, img));
        return id;
    }
}
