package dev.astroclient.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.astroclient.AstroClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Waypoints saved per world ("world:&lt;save folder&gt;") or server ("server:&lt;address&gt;") in
 * config/astroclient-waypoints.json. Only the name, block position and dimension the player saved are stored.
 */
public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Waypoint(String name, int x, int y, int z, String dimension) {}

    private final Path file;
    private final Map<String, List<Waypoint>> worlds = new LinkedHashMap<>();
    private boolean loaded;

    public WaypointStore(Path file) {
        this.file = file;
    }

    /** The waypoints of one world, created empty when it has none (live list; call {@link #save} after changing it). */
    public List<Waypoint> forWorld(String key) {
        load();
        return worlds.computeIfAbsent(key, k -> new ArrayList<>());
    }

    private void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(file)) return;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            JsonObject all = root.getAsJsonObject("worlds");
            if (all == null) return;
            for (Map.Entry<String, JsonElement> e : all.entrySet()) {
                if (!e.getValue().isJsonArray()) continue;
                List<Waypoint> list = new ArrayList<>();
                for (JsonElement el : e.getValue().getAsJsonArray()) {
                    if (!el.isJsonObject()) continue;
                    JsonObject o = el.getAsJsonObject();
                    if (!o.has("x") || !o.has("y") || !o.has("z")) continue;
                    list.add(new Waypoint(o.has("name") ? o.get("name").getAsString() : "WP", o.get("x").getAsInt(),
                            o.get("y").getAsInt(), o.get("z").getAsInt(),
                            o.has("dimension") ? o.get("dimension").getAsString() : "minecraft:overworld"));
                }
                worlds.put(e.getKey(), list);
            }
        } catch (IOException | RuntimeException e) {
            AstroClient.LOGGER.warn("Could not read {}, starting without waypoints", file, e);
        }
    }

    public void save() {
        JsonObject all = new JsonObject();
        for (Map.Entry<String, List<Waypoint>> e : worlds.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            JsonArray arr = new JsonArray();
            for (Waypoint w : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", w.name());
                o.addProperty("x", w.x());
                o.addProperty("y", w.y());
                o.addProperty("z", w.z());
                o.addProperty("dimension", w.dimension());
                arr.add(o);
            }
            all.add(e.getKey(), arr);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("worlds", all);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root));
        } catch (IOException e) {
            AstroClient.LOGGER.warn("Could not save {}", file, e);
        }
    }
}
