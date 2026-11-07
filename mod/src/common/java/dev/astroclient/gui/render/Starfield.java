package dev.astroclient.gui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/** Drifting, twinkling stars drawn with plain fills (works before textures are loaded). */
public final class Starfield {
    private final float[] x;
    private final float[] y;
    private final float[] depth;
    private final float[] phase;

    public Starfield(int count, long seed) {
        Random random = new Random(seed);
        x = new float[count];
        y = new float[count];
        depth = new float[count];
        phase = new float[count];
        for (int i = 0; i < count; i++) {
            x[i] = random.nextFloat();
            y[i] = random.nextFloat();
            depth[i] = 0.25f + random.nextFloat() * 0.75f;
            phase[i] = random.nextFloat() * MathHelper.TAU;
        }
    }

    public static void background(DrawContext context, int width, int height, float alpha) {
        context.fillGradient(0, 0, width, height, Theme.withAlpha(Theme.SPACE_TOP, alpha), Theme.withAlpha(Theme.SPACE_BOTTOM, alpha));
    }

    public void render(DrawContext context, int width, int height, float alpha) {
        float seconds = (Util.getMeasuringTimeMs() % 1_000_000L) / 1000f;
        for (int i = 0; i < x.length; i++) {
            float d = depth[i];
            float sx = (x[i] - seconds * 0.004f * d) % 1f;
            if (sx < 0) sx += 1f;
            int px = (int) (sx * width);
            int py = (int) (y[i] * height);
            float twinkle = 0.6f + 0.4f * MathHelper.sin(seconds * 1.6f + phase[i]);
            int a = Math.round(255 * alpha * d * twinkle);
            if (a <= 0) continue;
            int size = d > 0.85f ? 2 : 1;
            context.fill(px, py, px + size, py + size, (a << 24) | 0xDDE4FF);
        }
    }
}
