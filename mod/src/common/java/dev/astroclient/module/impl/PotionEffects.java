package dev.astroclient.module.impl;

import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.List;

/** Active potion effects with level and time left. */
public final class PotionEffects extends HudModule {
    private static final int LINE = 11;
    private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

    private final BooleanSetting background = add(new BooleanSetting("Background", true));

    public PotionEffects() {
        super("Potions", "Shows active effects and time left");
    }

    private record Line(String name, String time, boolean ending) {}

    private List<Line> lines(boolean editing) {
        List<Line> out = new ArrayList<>();
        if (mc.player != null) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                int level = effect.getAmplifier() + 1;
                String name = effect.getEffectType().value().getName().getString() + (level < ROMAN.length ? ROMAN[level] : " " + level);
                int seconds = effect.getDuration() / 20;
                String time = effect.isInfinite() ? "∞" : String.format("%d:%02d", seconds / 60, seconds % 60);
                out.add(new Line(name, time, !effect.isInfinite() && seconds <= 10));
            }
        }
        if (out.isEmpty() && editing) {
            out.add(new Line("Speed II", "1:30", false));
            out.add(new Line("Night Vision", "8:00", false));
        }
        return out;
    }

    @Override
    public boolean visible(boolean editing) {
        return !lines(editing).isEmpty();
    }

    @Override
    public int width(boolean editing) {
        int w = 0;
        for (Line l : lines(editing)) w = Math.max(w, mc.textRenderer.getWidth(l.name + "  " + l.time));
        return w + 14;
    }

    @Override
    public int height(boolean editing) {
        return lines(editing).size() * LINE + 7;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        List<Line> lines = lines(editing);
        int w = width(editing);
        if (background.get()) {
            RenderUtil.roundedRect(context, x, y, w, height(editing), 5, Theme.HUD_BACKGROUND);
            RenderUtil.roundedRect(context, x, y + 3, 2, height(editing) - 6, 1, Theme.ACCENT);
        }
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int ly = y + 4 + i * LINE;
            context.drawText(mc.textRenderer, l.name, x + 7, ly, Theme.TEXT, !background.get());
            int tw = mc.textRenderer.getWidth(l.time);
            // blink the timer in the last 10 seconds
            boolean dim = l.ending && (System.currentTimeMillis() / 500) % 2 == 0;
            context.drawText(mc.textRenderer, l.time, x + w - 7 - tw, ly, dim ? Theme.MUTED : Theme.ACCENT_2, !background.get());
        }
    }
}
