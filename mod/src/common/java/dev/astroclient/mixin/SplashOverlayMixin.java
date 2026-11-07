package dev.astroclient.mixin;

import dev.astroclient.gui.splash.AstroSplash;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Paints the Astro loading screen over vanilla's splash at the end of each frame.
 * Vanilla still runs its own render logic (reload tracking, fade-out, closing the overlay),
 * so this only mirrors its fade timings instead of re-implementing them.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {
    @Shadow @Final private boolean reloading;
    @Shadow private float progress;
    @Shadow private long reloadCompleteTime;
    @Shadow private long reloadStartTime;

    @Inject(method = "render", at = @At("TAIL"))
    private void astroclient$renderBrandedSplash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        long now = Util.getMeasuringTimeMs();
        // Same timings vanilla uses: fade in over 0.5 s on resource reloads, fade out 1-2 s after completion.
        float sinceComplete = reloadCompleteTime > -1L ? (now - reloadCompleteTime) / 1000f : -1f;
        float sinceStart = reloadStartTime > -1L ? (now - reloadStartTime) / 500f : -1f;

        float alpha;
        if (sinceComplete >= 1f) alpha = 1f - MathHelper.clamp(sinceComplete - 1f, 0f, 1f);
        else if (reloading) alpha = MathHelper.clamp(sinceStart, 0f, 1f);
        else alpha = 1f;

        if (alpha > 0f) AstroSplash.render(context, progress, alpha);
    }
}
