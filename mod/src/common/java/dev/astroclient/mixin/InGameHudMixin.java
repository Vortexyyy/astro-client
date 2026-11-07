package dev.astroclient.mixin;

import dev.astroclient.AstroClient;
import dev.astroclient.module.impl.Crosshair;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    /** Hides vanilla's crosshair while Astro's custom one is enabled. */
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void astroclient$customCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        Crosshair crosshair = AstroClient.get().modules().get(Crosshair.class);
        if (crosshair != null && crosshair.isEnabled()) ci.cancel();
    }
}
