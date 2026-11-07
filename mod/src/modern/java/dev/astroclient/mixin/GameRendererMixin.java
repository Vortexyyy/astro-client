package dev.astroclient.mixin;

import dev.astroclient.AstroClient;
import dev.astroclient.module.impl.Zoom;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** Divides the world FOV while zooming (getFov returns float since 1.21.2). The held-item pass stays unzoomed. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void astroclient$applyZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
        if (!changingFov) return;
        Zoom zoom = AstroClient.get().modules().get(Zoom.class);
        if (zoom == null || !zoom.isEnabled()) return;
        float zoomed = (float) zoom.apply(cir.getReturnValue());
        if (Float.isFinite(zoomed) && zoomed > 0f) cir.setReturnValue(zoomed);
    }
}
