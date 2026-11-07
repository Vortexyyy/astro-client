package dev.astroclient.mixin;

import dev.astroclient.AstroClient;
import dev.astroclient.module.impl.Freelook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** While Freelook is held, mouse movement turns the camera, not the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void astroclient$freelook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        Freelook freelook = AstroClient.get().modules().get(Freelook.class);
        if (freelook != null && freelook.active()) {
            freelook.turn(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
