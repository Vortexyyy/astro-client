package dev.astroclient.module.impl;

import dev.astroclient.compat.Gfx;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.ModeSetting;
import dev.astroclient.module.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;

/**
 * Custom crosshair drawn in screen pixels (crisp at any GUI scale). While enabled, vanilla's
 * crosshair is hidden by InGameHudMixin.
 */
public final class Crosshair extends Module {
    private final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Cross + dot", "Dot", "Circle"));
    private final ModeSetting color = add(new ModeSetting("Color", "White", "White", "Accent", "Cyan", "Green", "Red", "Yellow"));
    private final NumberSetting size = add(new NumberSetting("Size", 5, 2, 12, 1));
    private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 1));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 4, 1));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true));

    public Crosshair() {
        super("Crosshair", "Your own crosshair style and colour", Category.RENDER);
    }

    private int colour() {
        return switch (color.get()) {
            case "Accent" -> Theme.ACCENT;
            case "Cyan" -> Theme.ACCENT_2;
            case "Green" -> Theme.GOOD;
            case "Red" -> Theme.BAD;
            case "Yellow" -> Theme.WARN;
            default -> 0xFFFFFFFF;
        };
    }

    public void draw(DrawContext context) {
        if (mc.player == null || mc.player.isSpectator() || !mc.options.getPerspective().isFirstPerson()) return;
        float scale = Gfx.guiScale();
        // sizes are in "GUI scale 2" units so the crosshair looks the same at every GUI scale
        float unit = scale / 2f;
        int cx = Math.round(context.getScaledWindowWidth() * scale / 2f);
        int cy = Math.round(context.getScaledWindowHeight() * scale / 2f);
        int len = Math.max(1, Math.round(size.get().floatValue() * unit));
        int g = Math.round(gap.get().floatValue() * unit);
        int t = Math.max(1, Math.round(thickness.get().floatValue() * unit));

        Gfx.push(context);
        Gfx.scale(context, 1f / scale, 1f / scale);
        if (outline.get()) shape(context, cx, cy, len, g, t, 1, 0xC0000000);
        shape(context, cx, cy, len, g, t, 0, colour());
        Gfx.pop(context);
    }

    /** Draws the chosen style; {@code grow} > 0 draws it fattened by that many pixels (the outline). */
    private void shape(DrawContext c, int cx, int cy, int len, int gap, int t, int grow, int argb) {
        int half = t / 2;
        int x0 = cx - half - grow;
        int y0 = cy - half - grow;
        int tt = t + grow * 2;
        switch (style.get()) {
            case "Dot" -> c.fill(x0, y0, x0 + tt, y0 + tt, argb);
            case "Circle" -> ring(c, cx, cy, len + gap, t, grow, argb);
            default -> {
                c.fill(x0, cy - half - gap - len - grow, x0 + tt, cy - half - gap + grow, argb); // up
                c.fill(x0, cy - half + t + gap - grow, x0 + tt, cy - half + t + gap + len + grow, argb); // down
                c.fill(cx - half - gap - len - grow, y0, cx - half - gap + grow, y0 + tt, argb); // left
                c.fill(cx - half + t + gap - grow, y0, cx - half + t + gap + len + grow, y0 + tt, argb); // right
                if (style.is("Cross + dot")) c.fill(x0, y0, x0 + tt, y0 + tt, argb);
            }
        }
    }

    /** A ring of radius r and thickness t, one row of quads per pixel. */
    private static void ring(DrawContext c, int cx, int cy, int r, int t, int grow, int argb) {
        float outer = r + grow + 0.5f;
        float inner = Math.max(0, r - t - grow + 0.5f);
        int rows = (int) Math.ceil(outer);
        for (int dy = -rows; dy <= rows; dy++) {
            float y = dy + 0.5f;
            if (Math.abs(y) > outer) continue;
            int xo = (int) Math.round(Math.sqrt(outer * outer - y * y));
            int xi = Math.abs(y) < inner ? (int) Math.round(Math.sqrt(inner * inner - y * y)) : 0;
            if (xi == 0) c.fill(cx - xo, cy + dy, cx + xo, cy + dy + 1, argb);
            else {
                c.fill(cx - xo, cy + dy, cx - xi, cy + dy + 1, argb);
                c.fill(cx + xi, cy + dy, cx + xo, cy + dy + 1, argb);
            }
        }
    }
}
