package dev.astroclient.gui.render;

import dev.astroclient.gui.theme.ThemeSettings;
import net.minecraft.client.gui.DrawContext;

/** Shared building blocks for Astro's full-screen menus (worlds, servers, customize). */
public final class Ui {
    public static final int CARD = 0xFF17153A;
    public static final int CARD_HOVER = 0xFF221D4F;

    private Ui() {}

    /** Glass panel with a soft accent glow, using the player's panel opacity. */
    public static void panel(DrawContext context, int x, int y, int w, int h) {
        float alpha = ThemeSettings.get().panelAlpha();
        RenderUtil.glow(context, x, y, w, h, 10, Theme.withAlpha(Theme.ACCENT, 0.3f), 10);
        RenderUtil.roundedRect(context, x, y, w, h, 10, Theme.withAlpha(0xF20D0B22, alpha));
    }

    /** Thin accent gradient divider. */
    public static void divider(DrawContext context, int x1, int y, int x2) {
        RenderUtil.horizontalGradient(context, x1, y, x2, y + 1, Theme.withAlpha(Theme.ACCENT, 0.5f), Theme.withAlpha(Theme.ACCENT_2, 0.1f));
    }

    /** Placeholder tile with an initial, for worlds/servers without an icon. */
    public static void initialTile(DrawContext context, net.minecraft.client.font.TextRenderer font, String name, int x, int y, int size) {
        RenderUtil.roundedRect(context, x, y, size, size, 6, Theme.ACCENT);
        RenderUtil.roundedRect(context, x, y, size, size / 2f, 6, 0x22FFFFFF);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        context.drawText(font, initial, x + (size - font.getWidth(initial)) / 2, y + (size - 8) / 2, 0xFFFFFFFF, false);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
