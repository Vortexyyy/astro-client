package dev.astroclient.compat;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.AddServerScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PlayerSkinWidget;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.net.UnknownHostException;
import java.util.function.Consumer;

/** Version shim (1.21.1): constructors and registries whose signatures differ between versions. */
public final class Platform {
    private Platform() {}

    public static String minecraftVersion() {
        return SharedConstants.getGameVersion().getName();
    }

    public static void registerHud(Consumer<DrawContext> renderer) {
        HudRenderCallback.EVENT.register((context, tickCounter) -> renderer.accept(context));
    }

    /** Vanilla's rotatable 3D player preview showing the signed-in account's skin. */
    public static ClickableWidget playerModel(int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        return new PlayerSkinWidget(width, height, client.getEntityModelLoader(),
                client.getSkinProvider().getSkinTexturesSupplier(client.getGameProfile()));
    }

    public static void registerTexture(Identifier id, NativeImage image) {
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
    }

    public static void openCreateWorld(Screen parent) {
        CreateWorldScreen.create(MinecraftClient.getInstance(), parent);
    }

    public static Screen addServerScreen(Screen parent, BooleanConsumer callback, ServerInfo info, boolean editing) {
        return new AddServerScreen(parent, callback, info);
    }

    public static void pingServer(MultiplayerServerListPinger pinger, ServerInfo info, Runnable saver, Runnable done)
            throws UnknownHostException {
        pinger.add(info, saver, done);
    }
}
