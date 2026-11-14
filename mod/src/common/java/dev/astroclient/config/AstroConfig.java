package dev.astroclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astroclient.AstroClient;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.Module;
import dev.astroclient.module.ModuleManager;
import dev.astroclient.module.setting.Setting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists module on/off state and setting values to config/astroclient.json. */
public final class AstroConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;

    public AstroConfig(Path file) {
        this.file = file;
    }

    public void load(ModuleManager modules) {
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            for (Module module : modules.all()) {
                JsonElement el = root.get(module.getName());
                if (el == null || !el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("enabled")) module.setEnabled(obj.get("enabled").getAsBoolean());
                if (obj.has("pinned")) module.setPinned(obj.get("pinned").getAsBoolean());
                JsonObject hud = obj.getAsJsonObject("hud");
                if (module instanceof HudModule h && hud != null && hud.has("x") && hud.has("y")) {
                    h.setPosition(hud.get("x").getAsFloat(), hud.get("y").getAsFloat());
                }
                JsonObject settings = obj.getAsJsonObject("settings");
                if (settings == null) continue;
                for (Setting<?> setting : module.getSettings()) {
                    JsonElement value = settings.get(setting.getName());
                    if (value != null) setting.fromJson(value);
                }
            }
        } catch (IOException | RuntimeException e) {
            AstroClient.LOGGER.warn("Could not read {}, using defaults", file, e);
        }
    }

    public void save(ModuleManager modules) {
        JsonObject root = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("enabled", module.isEnabled());
            if (module.isPinned()) obj.addProperty("pinned", true);
            JsonObject settings = new JsonObject();
            for (Setting<?> setting : module.getSettings()) settings.add(setting.getName(), setting.toJson());
            obj.add("settings", settings);
            if (module instanceof HudModule h && h.hasPosition()) {
                JsonObject hud = new JsonObject();
                hud.addProperty("x", h.positionX());
                hud.addProperty("y", h.positionY());
                obj.add("hud", hud);
            }
            root.add(module.getName(), obj);
        }
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            AstroClient.LOGGER.warn("Could not save {}", file, e);
        }
    }
}
