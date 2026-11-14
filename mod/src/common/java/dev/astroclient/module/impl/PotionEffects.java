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

    private record Line(String name, String time, int timeWidth, boolean ending) {}

    private List<Line> cached = List.of();
    private int cachedWidth;

    /** Effect lines, rebuilt at the HUD refresh rate (names and timers change at most once a second). */
    private List<Line> current(boolean editing) {
        if (due(editing)) {
            cached = lines(editing);
            int w = 0;
            for (Line l : cached) w = Math.max(w, mc.textRenderer.getWidth(l.name) + mc.textRenderer.getWidth("  ") + l.timeWidth);
            cachedWidth = w + 14;
        }
        return cached;
    }

    private List<Line> lines(boolean editing) {
        List<Line> out = new ArrayList<>();
        if (mc.player != null) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                int level = effect.getAmplifier() + 1;
                String name = effect.getEffectType().value().getName().getString() + (level < ROMAN.length ? ROMAN[level] : " " + level);
                int seconds = effect.getDuration() / 20;
                String time = effect.isInfinite() ? "∞" : String.format("%d:%02d", seconds / 60, seconds % 60);
                out.add(new Line(name, time, mc.textRenderer.getWidth(time), !effect.isInfinite() && seconds <= 10));
            }
        }
        if (out.isEmpty() && editing) {
            out.add(new Line("Speed II", "1:30", mc.textRenderer.getWidth("1:30"), false));
            out.add(new Line("Night Vision", "8:00", mc.textRenderer.getWidth("8:00"), false));
        }
        return out;
    }

    @Override
    public boolean visible(boolean editing) {
        return !current(editing).isEmpty();
    }

    @Override
    public int width(boolean editing) {
        current(editing);
        return cachedWidth;
    }

    @Override
    public int height(boolean editing) {
        return current(editing).size() * LINE + 7;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        List<Line> lines = current(editing);
        int w = cachedWidth;
        int h = lines.size() * LINE + 7;
        if (background.get()) {
            boolean ending = false;
            for (Line l : lines) ending |= l.ending;
            // Orbit: an effect about to run out turns the frame orange
            RenderUtil.panel(context, x, y, w, h, Theme.R_MD, Theme.HUD_BACKGROUND, ending ? Theme.BAD : Theme.BORDER);
            if (!dev.astroclient.gui.theme.ThemeSettings.get().sharp()) RenderUtil.roundedRect(context, x, y + 3, 2, h - 6, 1, Theme.ACCENT);
        }
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int ly = y + 4 + i * LINE;
            context.drawText(mc.textRenderer, l.name, x + 7, ly, Theme.TEXT, !background.get());
            int tw = l.timeWidth;
            // blink the timer in the last 10 seconds
            boolean dim = l.ending && (System.currentTimeMillis() / 500) % 2 == 0;
            context.drawText(mc.textRenderer, l.time, x + w - 7 - tw, ly, l.ending ? (dim ? Theme.MUTED : Theme.BAD) : Theme.ACCENT, !background.get());
        }
    }
}
