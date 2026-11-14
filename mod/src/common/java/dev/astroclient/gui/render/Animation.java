package dev.astroclient.gui.render;

import net.minecraft.util.Util;

/**
 * Frame-rate independent value that eases toward a target (exponential smoothing),
 * so animations feel the same at 30 and 300 FPS.
 */
public final class Animation {
    /** Global speed multiplier from the "Animations" option in Customize (1 = normal). */
    private static float rate = 1f;
    /** Animations off: values jump straight to their target and decorative motion stands still. */
    private static boolean instant;

    private final float speed;
    private float value;
    private float target;
    private long lastUpdate = Util.getMeasuringTimeMs();

    /** @param speed higher is snappier; ~8 settles in about half a second */
    public Animation(float initial, float speed) {
        this.value = initial;
        this.target = initial;
        this.speed = speed;
    }

    public void setTarget(float target) {
        this.target = target;
    }

    /** Jumps straight to a value without animating. */
    public void snap(float value) {
        this.value = value;
        this.target = value;
    }

    public float getTarget() {
        return target;
    }

    public float update() {
        long now = Util.getMeasuringTimeMs();
        float dt = Math.min((now - lastUpdate) / 1000f, 0.1f);
        lastUpdate = now;
        if (instant) {
            value = target;
            return value;
        }
        value += (target - value) * (1f - (float) Math.exp(-speed * rate * dt));
        if (Math.abs(target - value) < 0.001f) value = target;
        return value;
    }

    public float get() {
        return value;
    }

    /** Sets the global animation speed; 0 or less turns animations off. */
    public static void rate(float multiplier) {
        instant = multiplier <= 0f;
        rate = instant ? 1f : multiplier;
    }

    /**
     * Seconds on a shared clock for decorative motion (shimmer, sheen, aurora). Runs at the global
     * animation speed and stands still when animations are off.
     */
    public static float clock() {
        return instant ? 0f : (Util.getMeasuringTimeMs() % 3_600_000L) / 1000f * rate;
    }

    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1f;
        float x = t - 1f;
        return 1f + c3 * x * x * x + c1 * x * x;
    }

    public static float easeOutCubic(float t) {
        float x = 1f - t;
        return 1f - x * x * x;
    }
}
