package dev.astroclient.gui.render;

import dev.astroclient.gui.theme.ThemeSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * The label every HUD module uses, so the HUD reads as one system. Orbit (Sharp corners): a graphite
 * chip with a hairline border, an accent square, a muted caps label and the value. Rounded corners keep
 * the classic pill with an accent bar.
 */
public final class HudPill {
    private static final int PAD_X = 6;
    private static final int MARK = 3;
    private static final int GAP = 4;
    public static final int HEIGHT = 16;

    private HudPill() {}

    private static TextRenderer font() {
        return MinecraftClient.getInstance().textRenderer;
    }

    public static int width(String text) {
        return width(null, text);
    }

    /** Width for an optional muted label followed by the value. */
    public static int width(String label, String text) {
        TextRenderer font = font();
        int w = font.getWidth(text) + PAD_X * 2;
        if (label != null) w += font.getWidth(label) + GAP;
        if (ThemeSettings.get().sharp()) w += MARK + GAP;
        return w;
    }

    public static int draw(DrawContext context, int x, int y, String text, boolean background) {
        return draw(context, x, y, null, text, width(null, text), background);
    }

    /** Same as above with the width already known (cached by the caller). */
    public static int draw(DrawContext context, int x, int y, String text, int width, boolean background) {
        return draw(context, x, y, null, text, width, background);
    }

    public static int draw(DrawContext context, int x, int y, String label, String text, int width, boolean background) {
        TextRenderer font = font();
        boolean sharp = ThemeSettings.get().sharp();
        int tx = x + PAD_X;
        if (background) {
            if (sharp) {
                RenderUtil.panel(context, x, y, width, HEIGHT, Theme.R_MD, Theme.HUD_BACKGROUND, Theme.BORDER);
            } else {
                RenderUtil.roundedRect(context, x, y, width, HEIGHT, 5, Theme.HUD_BACKGROUND);
                // glass sheen across the top half: one gradient quad
                if (ThemeSettings.get().effects != ThemeSettings.Quality.OFF) context.fillGradient(x + 3, y + 1, x + width - 3, y + 8, 0x1AFFFFFF, 0x00FFFFFF);
                // violet-to-cyan accent bar: one gradient quad
                context.fillGradient(x + 1, y + 3, x + 3, y + HEIGHT - 3, Theme.ACCENT, Theme.ACCENT_2);
            }
        }
        if (sharp) {
            RenderUtil.roundedRect(context, tx, y + (HEIGHT - MARK) / 2f, MARK, MARK, 0, Theme.ACCENT);
            tx += MARK + GAP;
        }
        int ty = y + (HEIGHT - 8) / 2;
        if (label != null) {
            context.drawText(font, label, tx, ty, Theme.MUTED, !background);
            tx += font.getWidth(label) + GAP;
        }
        context.drawText(font, text, tx, ty, Theme.TEXT, !background);
        return HEIGHT;
    }
}
