package dev.astroclient.module.impl;

import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.module.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

/**
 * WASD, mouse buttons and jump, lighting up while held. Idle keys are drawn as cheaply as possible
 * (one rounded shape and a label); glow, sheen and the colour wash only run for keys that are held or
 * still fading, so the module costs almost nothing while you are not pressing anything.
 */
public final class Keystrokes extends HudModule {
    private static final int KEY = 18;
    private static final int GAP = 2;
    private static final int SPACE_H = 8;
    private static final int COUNT = 7;
    /** below this fade value a key counts as idle */
    private static final float IDLE = 0.03f;

    private static final String[] LABELS = {"W", "A", "S", "D", "LMB", "RMB", ""};

    /** one fade per key, in the order above (no map lookups per frame) */
    private final Animation[] fades = new Animation[COUNT];
    private final int[] labelWidths = new int[COUNT];
    private boolean measured;

    public Keystrokes() {
        super("Keystrokes", "Shows movement keys and clicks");
        for (int i = 0; i < COUNT; i++) fades[i] = new Animation(0f, 20f);
    }

    @Override
    public int width(boolean editing) {
        return KEY * 3 + GAP * 2;
    }

    @Override
    public int height(boolean editing) {
        return (KEY + GAP) * 3 + SPACE_H;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        if (!measured) {
            for (int i = 0; i < COUNT; i++) labelWidths[i] = LABELS[i].isEmpty() ? 0 : mc.textRenderer.getWidth(LABELS[i]);
            measured = true;
        }
        int step = KEY + GAP;
        int fullW = KEY * 3 + GAP * 2;
        key(context, 0, mc.options.forwardKey, x + step, y, KEY, KEY);
        key(context, 1, mc.options.leftKey, x, y + step, KEY, KEY);
        key(context, 2, mc.options.backKey, x + step, y + step, KEY, KEY);
        key(context, 3, mc.options.rightKey, x + step * 2, y + step, KEY, KEY);
        int mouseW = (fullW - GAP) / 2;
        key(context, 4, mc.options.attackKey, x, y + step * 2, mouseW, KEY);
        key(context, 5, mc.options.useKey, x + mouseW + GAP, y + step * 2, fullW - mouseW - GAP, KEY);
        key(context, 6, mc.options.jumpKey, x, y + step * 3, fullW, SPACE_H);
    }

    private void key(DrawContext context, int index, KeyBinding binding, int x, int y, int w, int h) {
        Animation fade = fades[index];
        fade.setTarget(binding.isPressed() ? 1f : 0f);
        float p = fade.update();
        boolean idle = p < IDLE && fade.getTarget() < 0.5f;
        boolean sharp = ThemeSettings.get().sharp();

        if (idle) {
            // fast path: no colour mixing, no glow, no sheen
            if (sharp) RenderUtil.panel(context, x, y, w, h, Theme.R_MD, Theme.HUD_BACKGROUND, Theme.BORDER);
            else RenderUtil.roundedRect(context, x, y, w, h, Theme.R_MD, Theme.HUD_BACKGROUND);
            drawLabel(context, index, x, y, w, h, Theme.TEXT, Theme.MUTED);
            return;
        }

        if (sharp) {
            // Orbit: graphite key with a hairline border; held keys fill with the accent
            RenderUtil.panel(context, x, y, w, h, Theme.R_MD, Theme.lerp(Theme.HUD_BACKGROUND, Theme.ACCENT, p), Theme.lerp(Theme.BORDER, Theme.ACCENT, p));
        } else {
            // Nebula: a held key fills violet, glows and gets a cyan wash rising from its base
            boolean effects = ThemeSettings.get().effects != ThemeSettings.Quality.OFF;
            if (p > IDLE) RenderUtil.glow(context, x, y, w, h, Theme.R_MD, Theme.withAlpha(Theme.ACCENT_2, 0.5f * p), 4);
            RenderUtil.roundedRect(context, x, y, w, h, Theme.R_MD, Theme.lerp(Theme.HUD_BACKGROUND, Theme.ACCENT, p));
            if (effects && h >= 12) {
                context.fillGradient(x + 3, y + 1, x + w - 3, y + h / 2, 0x18FFFFFF, 0x00FFFFFF);
                if (p > IDLE) context.fillGradient(x + 3, y + h / 2, x + w - 3, y + h - 2, Theme.ACCENT_2 & 0x00FFFFFF, Theme.withAlpha(Theme.ACCENT_2, 0.5f * p));
            }
        }
        drawLabel(context, index, x, y, w, h, Theme.lerp(Theme.TEXT, Theme.ON_ACCENT, p), Theme.lerp(Theme.MUTED, Theme.ON_ACCENT, p));
    }

    private void drawLabel(DrawContext context, int index, int x, int y, int w, int h, int textColor, int barColor) {
        if (LABELS[index].isEmpty()) {
            RenderUtil.roundedRect(context, x + w / 2f - 8, y + h / 2f - 0.5f, 16, 1, 0.5f, barColor);
            return;
        }
        context.drawText(mc.textRenderer, LABELS[index], x + (w - labelWidths[index]) / 2, y + (h - 8) / 2 + 1, textColor, false);
    }
}
