package dev.astroclient.module.impl;

import dev.astroclient.module.TextHudModule;

public final class FpsDisplay extends TextHudModule {
    public FpsDisplay() {
        super("FPS", "Shows your frame rate");
        setEnabled(true);
    }

    @Override
    protected String label() {
        return "FPS";
    }

    @Override
    protected String text(boolean editing) {
        return String.valueOf(mc.getCurrentFps());
    }
}
