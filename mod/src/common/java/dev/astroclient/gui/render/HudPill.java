package dev.astroclient.gui.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** The rounded label every HUD module uses, so the HUD reads as one system. */
public final class HudPill {
    private static final int PAD_X = 6;
    public static final int HEIGHT = 16;

    private HudPill() {}

    public static int width(String text) {
        return MinecraftClient.getInstance().textRenderer.getWidth(text) + PAD_X * 2;
    }

    public static int draw(DrawContext context, int x, int y, String text, boolean background) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        int width = font.getWidth(text) + PAD_X * 2;
        if (background) {
            RenderUtil.roundedRect(context, x, y, width, HEIGHT, 5, Theme.HUD_BACKGROUND);
            RenderUtil.roundedRect(context, x, y + 3, 2, HEIGHT - 6, 1, Theme.ACCENT);
        }
        context.drawText(font, text, x + PAD_X, y + (HEIGHT - 8) / 2, Theme.TEXT, !background);
        return HEIGHT;
    }
}
