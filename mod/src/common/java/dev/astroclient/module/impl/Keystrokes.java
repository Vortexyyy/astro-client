package dev.astroclient.module.impl;

import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.HudModule;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;

import java.util.HashMap;
import java.util.Map;

/** WASD, mouse buttons and jump, lighting up while held. */
public final class Keystrokes extends HudModule {
    private static final int KEY = 18;
    private static final int GAP = 2;
    private static final int SPACE_H = 8;
    private static final int PRESSED = 0xE08B5CF6;

    private final Map<KeyBinding, Animation> fades = new HashMap<>();

    public Keystrokes() {
        super("Keystrokes", "Shows movement keys and clicks");
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
        int step = KEY + GAP;
        int fullW = KEY * 3 + GAP * 2;
        key(context, mc.options.forwardKey, "W", x + step, y, KEY, KEY);
        key(context, mc.options.leftKey, "A", x, y + step, KEY, KEY);
        key(context, mc.options.backKey, "S", x + step, y + step, KEY, KEY);
        key(context, mc.options.rightKey, "D", x + step * 2, y + step, KEY, KEY);
        int mouseW = (fullW - GAP) / 2;
        key(context, mc.options.attackKey, "LMB", x, y + step * 2, mouseW, KEY);
        key(context, mc.options.useKey, "RMB", x + mouseW + GAP, y + step * 2, fullW - mouseW - GAP, KEY);
        key(context, mc.options.jumpKey, "", x, y + step * 3, fullW, SPACE_H);
    }

    private void key(DrawContext context, KeyBinding binding, String label, int x, int y, int w, int h) {
        Animation fade = fades.computeIfAbsent(binding, b -> new Animation(0f, 20f));
        fade.setTarget(binding.isPressed() ? 1f : 0f);
        float p = fade.update();
        RenderUtil.roundedRect(context, x, y, w, h, 4, Theme.lerp(Theme.HUD_BACKGROUND, PRESSED, p));
        if (label.isEmpty()) {
            RenderUtil.roundedRect(context, x + w / 2f - 8, y + h / 2f - 0.5f, 16, 1, 0.5f, Theme.lerp(Theme.MUTED, 0xFFFFFFFF, p));
            return;
        }
        int textW = mc.textRenderer.getWidth(label);
        context.drawText(mc.textRenderer, label, x + (w - textW) / 2, y + (h - 8) / 2 + 1,
                Theme.lerp(Theme.TEXT, 0xFFFFFFFF, p), false);
    }
}
