package dev.astroclient.module.impl;

import dev.astroclient.module.TextHudModule;
import dev.astroclient.module.setting.NumberSetting;

public final class Coordinates extends TextHudModule {
    private final NumberSetting decimals = add(new NumberSetting("Decimals", 0, 0, 2, 1));

    public Coordinates() {
        super("Coordinates", "Shows your XYZ position");
    }

    @Override
    protected String label() {
        return "XYZ";
    }

    @Override
    protected String text(boolean editing) {
        String fmt = "%." + decimals.get().intValue() + "f";
        if (mc.player == null) return editing ? String.format(fmt + " / " + fmt + " / " + fmt, 0.0, 64.0, 0.0) : null;
        return String.format(fmt + " / " + fmt + " / " + fmt, mc.player.getX(), mc.player.getY(), mc.player.getZ());
    }
}
