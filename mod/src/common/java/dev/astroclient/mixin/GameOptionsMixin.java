package dev.astroclient.mixin;

import dev.astroclient.AstroClient;
import dev.astroclient.module.impl.Fullbright;
import net.minecraft.client.option.GameOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameOptions.class)
public abstract class GameOptionsMixin {
    /** options.txt always gets the player's real brightness, never Fullbright's. */
    @Inject(method = "write", at = @At("HEAD"))
    private void astroclient$saveRealGamma(CallbackInfo ci) {
        Fullbright fullbright = fullbright();
        if (fullbright != null) fullbright.beforeOptionsSave();
    }

    @Inject(method = "write", at = @At("RETURN"))
    private void astroclient$restoreFullbright(CallbackInfo ci) {
        Fullbright fullbright = fullbright();
        if (fullbright != null) fullbright.afterOptionsSave();
    }

    private static Fullbright fullbright() {
        AstroClient astro = AstroClient.get();
        return astro == null ? null : astro.modules().get(Fullbright.class);
    }
}
