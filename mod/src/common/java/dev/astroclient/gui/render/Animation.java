package dev.astroclient.gui.render;

import net.minecraft.util.Util;

/**
 * Frame-rate independent value that eases toward a target (exponential smoothing),
 * so animations feel the same at 30 and 300 FPS.
 */
public final class Animation {
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
        value += (target - value) * (1f - (float) Math.exp(-speed * dt));
        if (Math.abs(target - value) < 0.001f) value = target;
        return value;
    }

    public float get() {
        return value;
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
