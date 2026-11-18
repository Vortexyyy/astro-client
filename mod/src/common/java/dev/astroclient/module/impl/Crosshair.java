package dev.astroclient.module.impl;

import dev.astroclient.compat.Gfx;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.ModeSetting;
import dev.astroclient.module.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Custom crosshair drawn in screen pixels (crisp at any GUI scale). While enabled, vanilla's
 * crosshair is hidden by InGameHudMixin. Presets fill in style/size/gap/thickness; editing any of
 * those afterwards switches the preset back to Custom. Dynamic gap opens the arms while sprinting and
 * while the attack cooldown recovers.
 */
public final class Crosshair extends Module {
    /** style, size, gap, thickness for each named preset. */
    private static final String[] PRESET_NAMES = {"Classic", "Dot", "Sniper"};
    private static final String[] PRESET_STYLE = {"Cross", "Dot", "Cross + dot"};
    private static final int[][] PRESET_VALUES = {{5, 2, 1}, {5, 0, 2}, {10, 4, 1}};

    private final ModeSetting style = add(new ModeSetting("Style", "Cross", "Cross", "Cross + dot", "Dot", "Circle"));
    private final ModeSetting color = add(new ModeSetting("Color", "White", "White", "Accent", "Cyan", "Green", "Red", "Yellow"));
    private final NumberSetting size = add(new NumberSetting("Size", 5, 2, 12, 1));
    private final NumberSetting gap = add(new NumberSetting("Gap", 2, 0, 8, 1));
    private final NumberSetting thickness = add(new NumberSetting("Thickness", 1, 1, 4, 1));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true));
    private final ModeSetting preset = add(new ModeSetting("Preset", "Custom", "Custom", "Classic", "Dot", "Sniper"));
    private final BooleanSetting dynamicGap = add(new BooleanSetting("Dynamic gap", false));
    private final NumberSetting spread = add(new NumberSetting("Gap spread", 3, 1, 8, 1));
    private final BooleanSetting playerColour = add(new BooleanSetting("Recolor on players", false));
    private final ModeSetting targetColor = add(new ModeSetting("Player color", "Red", "Red", "Accent", "Cyan", "Green", "Yellow", "White"));
    private final Animation open = new Animation(0f, 14f);
    private String appliedPreset;

    public Crosshair() {
        super("Crosshair", "Your own crosshair style and colour", Category.RENDER);
    }

    private int colour() {
        String c = playerColour.get() && mc.targetedEntity instanceof PlayerEntity ? targetColor.get() : color.get();
        return switch (c) {
            case "Accent" -> Theme.ACCENT;
            case "Cyan" -> Theme.ACCENT_2;
            case "Green" -> Theme.GOOD;
            case "Red" -> Theme.BAD;
            case "Yellow" -> Theme.WARN;
            default -> 0xFFFFFFFF;
        };
    }

    @Override
    public void onTick() {
        syncPreset();
    }

    /** Applies a newly picked preset, or drops back to Custom once its values were edited by hand. */
    private void syncPreset() {
        String p = preset.get();
        if (appliedPreset == null) appliedPreset = p; // just loaded: the saved values already match
        int i = presetIndex(p);
        if (!p.equals(appliedPreset)) {
            appliedPreset = p;
            if (i < 0) return;
            style.set(PRESET_STYLE[i]);
            size.set((double) PRESET_VALUES[i][0]);
            gap.set((double) PRESET_VALUES[i][1]);
            thickness.set((double) PRESET_VALUES[i][2]);
        } else if (i >= 0 && (!style.is(PRESET_STYLE[i]) || size.get() != PRESET_VALUES[i][0]
                || gap.get() != PRESET_VALUES[i][1] || thickness.get() != PRESET_VALUES[i][2])) {
            preset.set("Custom");
            appliedPreset = "Custom";
        }
    }

    private static int presetIndex(String name) {
        for (int i = 0; i < PRESET_NAMES.length; i++) if (PRESET_NAMES[i].equals(name)) return i;
        return -1;
    }

    /** Extra gap (in the same units as Gap) from sprinting and the attack cooldown, eased. */
    private float dynamicOpen() {
        if (!dynamicGap.get()) {
            open.snap(0f);
            return 0f;
        }
        float target = 0f;
        if (mc.player.isSprinting()) target += 0.5f;
        target += 1f - mc.player.getAttackCooldownProgress(0f);
        open.setTarget(Math.min(1f, target) * spread.get().floatValue());
        return open.update();
    }

    public void draw(DrawContext context) {
        if (mc.player == null || mc.player.isSpectator() || !mc.options.getPerspective().isFirstPerson()) return;
        syncPreset();
        float scale = Gfx.guiScale();
        // sizes are in "GUI scale 2" units so the crosshair looks the same at every GUI scale
        float unit = scale / 2f;
        int cx = Math.round(context.getScaledWindowWidth() * scale / 2f);
        int cy = Math.round(context.getScaledWindowHeight() * scale / 2f);
        int len = Math.max(1, Math.round(size.get().floatValue() * unit));
        int g = Math.round((gap.get().floatValue() + dynamicOpen()) * unit);
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
