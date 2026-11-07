package dev.astroclient.mixin;

import dev.astroclient.AstroClient;
import dev.astroclient.module.impl.Freelook;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(Camera.class)
public abstract class CameraMixin {
    /** While Freelook is held, the camera uses the free-look angle instead of the player's. */
    @ModifyArgs(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V"))
    private void astroclient$freelook(Args args) {
        Freelook freelook = AstroClient.get().modules().get(Freelook.class);
        if (freelook != null && freelook.active()) {
            args.set(0, freelook.yaw());
            args.set(1, freelook.pitch());
        }
    }
}
