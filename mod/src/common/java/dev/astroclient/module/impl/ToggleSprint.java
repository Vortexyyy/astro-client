package dev.astroclient.module.impl;

import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import net.minecraft.client.network.ClientPlayerEntity;

public final class ToggleSprint extends Module {
    private static final int MIN_SPRINT_FOOD = 7;

    public ToggleSprint() {
        super("Toggle Sprint", "Sprint automatically while moving forward", Category.MOVEMENT);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;
        boolean canSprint = player.getHungerManager().getFoodLevel() >= MIN_SPRINT_FOOD || player.getAbilities().allowFlying;
        if (player.input.getMovementInput().y > 0 && canSprint && !player.isSneaking()
                && !player.horizontalCollision && !player.isUsingItem()) {
            player.setSprinting(true);
        }
    }
}
