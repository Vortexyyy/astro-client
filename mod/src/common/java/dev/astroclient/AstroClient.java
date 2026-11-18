package dev.astroclient;

import dev.astroclient.bench.Benchmark;
import dev.astroclient.compat.Keys;
import dev.astroclient.compat.Platform;
import dev.astroclient.config.AstroConfig;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.menu.ModMenuScreen;
import dev.astroclient.module.ModuleManager;
import dev.astroclient.module.impl.Waypoints;
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
    private KeyBinding waypointAddKey;
    private KeyBinding waypointDeleteKey;
    private KeyBinding waypointClearKey;

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
        waypointAddKey = Keys.register("key.astroclient.waypoint_add", GLFW.GLFW_KEY_B);
        // unbound by default so nobody wipes waypoints by accident
        waypointDeleteKey = Keys.register("key.astroclient.waypoint_delete", GLFW.GLFW_KEY_UNKNOWN);
        waypointClearKey = Keys.register("key.astroclient.waypoint_clear", GLFW.GLFW_KEY_UNKNOWN);
        Waypoints waypoints = modules.get(Waypoints.class);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Key bindings only fire while no screen is open; ModMenuScreen handles closing itself.
            while (clickGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new ModMenuScreen());
            }
            // drained even while Waypoints is off, so presses don't pile up until it's turned on
            boolean wp = waypoints != null && waypoints.isEnabled() && client.player != null;
            while (waypointAddKey.wasPressed()) if (wp) waypoints.addHere();
            while (waypointDeleteKey.wasPressed()) if (wp) waypoints.deleteLast();
            while (waypointClearKey.wasPressed()) if (wp) waypoints.clearAll();
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

    public KeyBinding waypointAddKey() {
        return waypointAddKey;
    }

    /** The key a binding is set to, as shown in Controls ("C", "Left Alt"), or null when unbound. */
    public static String keyName(KeyBinding binding) {
        return binding == null || binding.isUnbound() ? null : binding.getBoundKeyLocalizedText().getString();
    }

    public void saveConfig() {
        if (Benchmark.active()) return; // benchmark toggles modules; keep the player's config as it was
        config.save(modules);
    }
}
