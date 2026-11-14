package dev.astroclient.gui.render;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

/** Drifting, twinkling stars drawn with plain fills (works before textures are loaded). */
public final class Starfield {
    // -- Tunables --
    /** how far (GUI px) the nearest stars shift as the pointer crosses the screen; 0 turns parallax off */
    private static final float PARALLAX = 10f;
    /** seconds between shooting stars; each one lasts SHOOTING_STAR_LIFE seconds */
    private static final float SHOOTING_STAR_EVERY = 7f;
    private static final float SHOOTING_STAR_LIFE = 0.9f;

    private static float pointerX = 0.5f;
    private static float pointerY = 0.5f;

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

    /** Pointer position (0..1 across the screen) for the parallax; screens that don't call this get a still field. */
    public static void pointer(float x, float y) {
        pointerX = MathHelper.clamp(x, 0f, 1f);
        pointerY = MathHelper.clamp(y, 0f, 1f);
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
            int px = (int) (sx * width - (pointerX - 0.5f) * PARALLAX * d);
            int py = (int) (y[i] * height - (pointerY - 0.5f) * PARALLAX * d);
            float twinkle = 0.6f + 0.4f * MathHelper.sin(seconds * 1.6f + phase[i]);
            int a = Math.round(255 * alpha * d * twinkle);
            if (a <= 0) continue;
            int size = d > 0.85f ? 2 : 1;
            context.fill(px, py, px + size, py + size, (a << 24) | 0xDDE4FF);
        }
        shootingStar(context, width, height, alpha, seconds);
    }

    /** One streak every few seconds, derived from the clock so it needs no state between frames. */
    private static void shootingStar(DrawContext context, int width, int height, float alpha, float seconds) {
        int index = (int) (seconds / SHOOTING_STAR_EVERY);
        float life = (seconds - index * SHOOTING_STAR_EVERY) / SHOOTING_STAR_LIFE;
        if (life >= 1f) return;
        Random random = new Random(index * 7919L);
        float startX = (0.25f + random.nextFloat() * 0.7f) * width;
        float startY = random.nextFloat() * 0.4f * height;
        float travel = 0.3f * width;
        for (int k = 0; k < 14; k++) {
            float q = life - k * 0.018f; // the tail trails behind the head
            if (q < 0f) continue;
            int px = (int) (startX - q * travel);
            int py = (int) (startY + q * travel * 0.5f);
            int a = Math.round(255 * alpha * (1f - life) * (1f - k / 14f));
            if (a <= 0) continue;
            int size = k < 2 ? 2 : 1;
            context.fill(px, py, px + size, py + size, (a << 24) | 0xFFFFFF);
        }
    }
}
