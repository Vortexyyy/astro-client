package dev.astroclient.gui.render;

import dev.astroclient.gui.theme.ThemeSettings;
import net.minecraft.client.gui.DrawContext;

/** Shared building blocks for Astro's full-screen menus (worlds, servers, customize). */
public final class Ui {
    public static final int CARD = Theme.CARD;
    public static final int CARD_HOVER = Theme.CARD_HOVER;

    private Ui() {}

    /** Glass panel with a soft accent glow, using the player's panel opacity. */
    public static void panel(DrawContext context, int x, int y, int w, int h) {
        float alpha = ThemeSettings.get().panelAlpha();
        if (ThemeSettings.get().sharp()) {
            RenderUtil.panel(context, x, y, w, h, Theme.R_XL, Theme.withAlpha(Theme.PANEL_DEEP, alpha), Theme.BORDER);
            return;
        }
        RenderUtil.glow(context, x, y, w, h, Theme.R_XL, Theme.withAlpha(Theme.ACCENT, 0.3f), 10);
        RenderUtil.surface(context, x, y, w, h, Theme.R_XL, Theme.withAlpha(Theme.PANEL_DEEP, alpha), Theme.BORDER_STRONG);
        // the Nebula signature: a violet-to-cyan line of light along the top edge
        RenderUtil.shimmerLine(context, x + 10, y + 1, x + w - 10, 1, 0.9f);
    }

    /** Thin accent gradient divider. */
    public static void divider(DrawContext context, int x1, int y, int x2) {
        if (ThemeSettings.get().sharp()) {
            context.fill(x1, y, x2, y + 1, Theme.BORDER);
            context.fill(x1, y, x1 + 12, y + 1, Theme.ACCENT);
            return;
        }
        RenderUtil.horizontalGradient(context, x1, y, x2, y + 1, Theme.withAlpha(Theme.ACCENT, 0.55f), Theme.withAlpha(Theme.ACCENT_2, 0.12f));
    }

    /** Placeholder tile with an initial, for worlds/servers without an icon. */
    public static void initialTile(DrawContext context, net.minecraft.client.font.TextRenderer font, String name, int x, int y, int size) {
        RenderUtil.roundedRect(context, x, y, size, size, Theme.R_MD, Theme.ACCENT);
        if (!ThemeSettings.get().sharp()) RenderUtil.roundedRect(context, x, y, size, size / 2f, Theme.R_MD, 0x22FFFFFF);
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        context.drawText(font, initial, x + (size - font.getWidth(initial)) / 2, y + (size - 8) / 2, Theme.ON_ACCENT, false);
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
