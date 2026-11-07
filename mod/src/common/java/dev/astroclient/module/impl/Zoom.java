package dev.astroclient.module.impl;

import dev.astroclient.AstroClient;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.NumberSetting;

/** Hold the zoom key (C by default) to zoom in. Applied to the world FOV by GameRendererMixin. */
public final class Zoom extends Module {
    private final NumberSetting factor = add(new NumberSetting("Zoom level", 4, 2, 10, 0.5));
    private final BooleanSetting smooth = add(new BooleanSetting("Smooth", true));
    private final Animation current = new Animation(1f, 14f);

    public Zoom() {
        super("Zoom", "Hold C to zoom in", Category.RENDER);
        setEnabled(true);
    }

    public double apply(double fov) {
        boolean held = isEnabled() && mc.currentScreen == null && AstroClient.get().zoomKey().isPressed();
        float target = held ? factor.get().floatValue() : 1f;
        if (smooth.get()) current.setTarget(target);
        else current.snap(target);
        return fov / current.update();
    }
}
