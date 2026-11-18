package dev.astroclient.mixin;

import dev.astroclient.module.impl.ComboDisplay;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    /** Tells the combo counter which entity the player just attacked (observe only, nothing changed). */
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void astroclient$noteAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        ComboDisplay.onAttack(target);
    }
}
