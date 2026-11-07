package dev.astroclient.module.impl;

import dev.astroclient.mixin.SimpleOptionAccessor;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;

/**
 * Raises brightness far past the slider's limit so caves and nights are fully lit. The player's own
 * brightness is restored when this is turned off, and it is what gets written to options.txt
 * (GameOptionsMixin), so a crash never leaves the game stuck at 1600%.
 */
public final class Fullbright extends Module {
    private static final double GAMMA = 16.0;
    private Double saved;

    public Fullbright() {
        super("Fullbright", "See clearly in caves and at night", Category.RENDER);
    }

    @Override
    public void onTick() {
        if (saved == null) saved = Math.min(mc.options.getGamma().getValue(), 1.0);
        if (mc.options.getGamma().getValue() != GAMMA) setRaw(GAMMA);
    }

    @Override
    protected void onDisable() {
        if (saved != null) setRaw(saved);
        saved = null;
    }

    private void setRaw(double value) {
        ((SimpleOptionAccessor) (Object) mc.options.getGamma()).astroclient$setRawValue(value);
    }

    /** Called around GameOptions.write(): save the real brightness, then re-apply fullbright. */
    public void beforeOptionsSave() {
        if (saved != null) setRaw(saved);
    }

    public void afterOptionsSave() {
        if (saved != null && isEnabled()) setRaw(GAMMA);
    }
}
