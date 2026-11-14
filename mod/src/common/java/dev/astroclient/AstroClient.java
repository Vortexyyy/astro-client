package dev.astroclient;

import dev.astroclient.bench.Benchmark;
import dev.astroclient.compat.Keys;
import dev.astroclient.compat.Platform;
import dev.astroclient.config.AstroConfig;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.menu.ModMenuScreen;
import dev.astroclient.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AstroClient implements ClientModInitializer {
    public static final String MOD_ID = "astroclient";
    public static final Logger LOGGER = LoggerFactory.getLogger("Astro Client");

    private static AstroClient instance;

    private final ModuleManager modules = new ModuleManager();
    private final AstroConfig config = new AstroConfig(FabricLoader.getInstance().getConfigDir().resolve("astroclient.json"));
    private KeyBinding clickGuiKey;
    private KeyBinding zoomKey;
    private KeyBinding freelookKey;

    public static AstroClient get() {
        return instance;
    }

    @Override
    public void onInitializeClient() {
        instance = this;
        modules.registerDefaults();
        config.load(modules);
        ThemeSettings.load(FabricLoader.getInstance().getConfigDir());
        Backgrounds.init(FabricLoader.getInstance().getConfigDir());

        clickGuiKey = Keys.register("key.astroclient.clickgui", GLFW.GLFW_KEY_RIGHT_SHIFT);
        zoomKey = Keys.register("key.astroclient.zoom", GLFW.GLFW_KEY_C);
        freelookKey = Keys.register("key.astroclient.freelook", GLFW.GLFW_KEY_LEFT_ALT);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Key bindings only fire while no screen is open; ModMenuScreen handles closing itself.
            while (clickGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ModMenuScreen());
            }
            modules.tick(client);
            Benchmark.tick(client);
        });
        Platform.registerHud(modules::renderHud);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveConfig());

        LOGGER.info("Astro Client initialised with {} modules", modules.all().size());
    }

    public ModuleManager modules() {
        return modules;
    }

    public KeyBinding clickGuiKey() {
        return clickGuiKey;
    }

    public KeyBinding zoomKey() {
        return zoomKey;
    }

    public KeyBinding freelookKey() {
        return freelookKey;
    }

    public void saveConfig() {
        if (Benchmark.active()) return; // benchmark toggles modules; keep the player's config as it was
        config.save(modules);
    }
}
