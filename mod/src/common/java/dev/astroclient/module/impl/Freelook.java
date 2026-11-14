package dev.astroclient.module.impl;

import dev.astroclient.AstroClient;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/**
 * Hold the Freelook key (Left Alt) to look around without turning your player. Mouse movement goes
 * to a separate camera angle (EntityMixin) which the camera uses instead of the player's (CameraMixin).
 */
public final class Freelook extends Module {
    private static final float SENSITIVITY = 0.15f; // same factor vanilla uses for turning

    private final BooleanSetting thirdPerson = add(new BooleanSetting("Third person", true));
    private boolean active;
    private float yaw;
    private float pitch;
    private Perspective previous;

    public Freelook() {
        super("Freelook", "Hold Left Alt to look around without turning", Category.RENDER);
        setEnabled(true);
    }

    @Override
    public String keyHint() {
        return AstroClient.keyName(AstroClient.get().freelookKey());
    }

    @Override
    public void onTick() {
        boolean held = mc.currentScreen == null && AstroClient.get().freelookKey().isPressed();
        if (held && !active) start();
        else if (!held && active) stop();
    }

    @Override
    protected void onDisable() {
        if (active) stop();
    }

    private void start() {
        if (mc.player == null) return;
        active = true;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        previous = mc.options.getPerspective();
        if (thirdPerson.get()) mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
    }

    private void stop() {
        active = false;
        if (previous != null) mc.options.setPerspective(previous);
        previous = null;
    }

    public boolean active() {
        return active && isEnabled();
    }

    public void turn(double cursorDeltaX, double cursorDeltaY) {
        yaw += (float) cursorDeltaX * SENSITIVITY;
        pitch = MathHelper.clamp(pitch + (float) cursorDeltaY * SENSITIVITY, -90f, 90f);
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}
