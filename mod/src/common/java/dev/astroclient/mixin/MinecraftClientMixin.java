package dev.astroclient.mixin;

import dev.astroclient.gui.servers.ServersScreen;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.title.AstroTitleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    /**
     * Every path back to the main menu goes through setScreen(new TitleScreen(...)), and vanilla opens
     * MultiplayerScreen after the first-time warning and after disconnecting; swap in Astro's versions.
     * Only the exact vanilla classes are replaced, so other mods' subclasses are left alone.
     */
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private Screen astroclient$replaceMenus(Screen screen) {
        if (!ThemeSettings.get().customMenus) return screen;
        // setScreen(null) with no world builds vanilla's TitleScreen inside the method, after this hook,
        // so closing any menu at the main menu would otherwise land on the vanilla title screen
        if (screen == null) return ((MinecraftClient) (Object) this).world == null ? new AstroTitleScreen() : null;
        if (screen.getClass() == TitleScreen.class) return new AstroTitleScreen();
        if (screen.getClass() == MultiplayerScreen.class) return new ServersScreen(new AstroTitleScreen());
        return screen;
    }
}
