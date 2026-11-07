package dev.astroclient.gui.splash;

import dev.astroclient.gui.render.PixelFont;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Starfield;
import dev.astroclient.gui.render.Theme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

/**
 * Branded loading screen, drawn over vanilla's {@code SplashOverlay} by SplashOverlayMixin.
 * Uses only fills (no fonts/textures) because it is visible while resources are still loading.
 */
public final class AstroSplash {
    private static final Starfield STARS = new Starfield(140, 0xA57D0L);
    private static final int BAR_WIDTH = 180;
    private static final int BAR_HEIGHT = 4;

    private AstroSplash() {}

    public static void render(DrawContext context, float progress, float alpha) {
        int w = context.getScaledWindowWidth();
        int h = context.getScaledWindowHeight();
        int cx = w / 2;
        int cy = h / 2;

        Starfield.background(context, w, h, alpha);
        STARS.render(context, w, h, alpha);

        // Pulsing orb above the wordmark
        float pulse = 0.5f + 0.5f * MathHelper.sin((Util.getMeasuringTimeMs() % 100_000L) / 450f);
        float orbY = cy - 46;
        RenderUtil.glow(context, cx - 10, orbY - 10, 20, 20, 10, Theme.withAlpha(Theme.ACCENT, alpha * (0.5f + 0.4f * pulse)), 10);
        RenderUtil.circle(context, cx, orbY, 10, Theme.withAlpha(Theme.ACCENT, alpha));
        RenderUtil.circle(context, cx - 2.5f, orbY - 2.5f, 5, Theme.withAlpha(Theme.ACCENT_2, alpha));
        RenderUtil.circle(context, cx - 4, orbY - 4, 1.5f, Theme.withAlpha(0xFFFFFFFF, alpha));

        // Wordmark
        int scale = Math.max(2, Math.min(5, w / 110));
        int titleY = cy - 18;
        PixelFont.drawCentered(context, "ASTRO", cx, titleY, scale, scale * 2,
                Theme.withAlpha(0xFFFFFFFF, alpha), Theme.withAlpha(Theme.ACCENT, alpha));
        int subScale = Math.max(1, scale / 2);
        PixelFont.drawCentered(context, "CLIENT", cx, titleY + PixelFont.height(scale) + 8, subScale, subScale * 4,
                Theme.withAlpha(Theme.ACCENT_2, alpha), Theme.withAlpha(Theme.ACCENT_2, alpha));

        // Progress bar
        int barX = cx - BAR_WIDTH / 2;
        int barY = titleY + PixelFont.height(scale) + PixelFont.height(subScale) + 28;
        RenderUtil.roundedRect(context, barX, barY, BAR_WIDTH, BAR_HEIGHT, 2, Theme.withAlpha(Theme.TRACK, alpha));
        float filled = BAR_WIDTH * MathHelper.clamp(progress, 0f, 1f);
        if (filled >= 1f) {
            RenderUtil.glow(context, barX, barY, filled, BAR_HEIGHT, 2, Theme.withAlpha(Theme.ACCENT_2, alpha * 0.6f), 4);
            RenderUtil.roundedRect(context, barX, barY, filled, BAR_HEIGHT, 2, Theme.withAlpha(Theme.ACCENT, alpha));
        }
    }
}
