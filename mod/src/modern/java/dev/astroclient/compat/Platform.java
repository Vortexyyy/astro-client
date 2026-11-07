package dev.astroclient.compat;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.net.UnknownHostException;
import java.util.function.Consumer;

/** Version shim (1.21.9+): constructors and registries whose signatures differ between versions. */
public final class Platform {
    private Platform() {}

    public static String minecraftVersion() {
        return SharedConstants.getGameVersion().name();
    }

    public static void registerHud(Consumer<DrawContext> renderer) {
        HudElementRegistry.addLast(Identifier.of("astroclient", "hud"), (context, tickCounter) -> renderer.accept(context));
    }

    /** Vanilla's rotatable 3D player preview showing the signed-in account's skin. */
    public static ClickableWidget playerModel(int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        return new PlayerSkinWidget(width, height, client.getLoadedEntityModels(),
                client.getSkinProvider().supplySkinTextures(client.getGameProfile(), false));
    }

    public static void registerTexture(Identifier id, NativeImage image) {
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, new NativeImageBackedTexture(id::toString, image));
    }

    public static void openCreateWorld(Screen parent) {
        MinecraftClient client = MinecraftClient.getInstance();
        CreateWorldScreen.show(client, () -> client.setScreen(parent));
    }

    public static Screen addServerScreen(Screen parent, BooleanConsumer callback, ServerInfo info, boolean editing) {
        Text title = Text.translatable(editing ? "manageServer.edit.title" : "manageServer.add.title");
        return new AddServerScreen(parent, title, callback, info);
    }

    public static void pingServer(MultiplayerServerListPinger pinger, ServerInfo info, Runnable saver, Runnable done)
            throws UnknownHostException {
        ServerPing.add(pinger, info, saver, done);
    }
}
