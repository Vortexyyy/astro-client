package dev.astroclient.module.impl;

import dev.astroclient.AstroClient;
import dev.astroclient.config.WaypointStore;
import dev.astroclient.config.WaypointStore.Waypoint;
import dev.astroclient.gui.render.HudPill;
import dev.astroclient.gui.render.Icons;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.MathHelper;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Named waypoints saved at your position with a key (auto-named "WP 1", "WP 2"...), stored per world or
 * server. The HUD shows the nearest one in your dimension with its distance and an arrow relative to
 * where you are looking.
 */
public final class Waypoints extends HudModule {
    private static final int ARROW = 7;
    private static final int ARROW_GAP = 4;

    private final BooleanSetting saveHere = add(new BooleanSetting("Save here (click)", false));
    private final BooleanSetting deleteLast = add(new BooleanSetting("Delete last (click)", false));
    private final BooleanSetting hideEmpty = add(new BooleanSetting("Hide when none", false));
    private final BooleanSetting background = add(new BooleanSetting("Background", true));

    private final WaypointStore store;
    private boolean primed;
    private int worldId;
    private String worldKey;
    private String dimension = "";
    private List<Waypoint> list;

    // HUD cache, rebuilt at the HUD refresh rate; strings only when their value changes
    private boolean cachedVisible;
    private String shownName = "WP";
    private String shownDistance = "None";
    private int distance = Integer.MIN_VALUE;
    private int arrow = -1;
    private int cachedWidth;
    private boolean cachedSharp;

    public Waypoints() {
        super("Waypoints", "Save spots with a key; shows the nearest one");
        store = new WaypointStore(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("astroclient-waypoints.json"));
    }

    @Override
    public String keyHint() {
        return AstroClient.keyName(AstroClient.get().waypointAddKey());
    }

    @Override
    public void onTick() {
        syncWorld();
        if (!primed) {
            // a click can't be pending yet: ignore whatever the config held
            primed = true;
            saveHere.set(false);
            deleteLast.set(false);
            return;
        }
        if (saveHere.get()) {
            saveHere.set(false);
            addHere();
        }
        if (deleteLast.get()) {
            deleteLast.set(false);
            deleteLast();
        }
    }

    /** Recomputes the world key and dimension when the client world changes (joining, dimension travel). */
    private void syncWorld() {
        if (mc.world == null) return;
        int id = System.identityHashCode(mc.world);
        if (id == worldId && list != null) return;
        worldId = id;
        String key = worldKey();
        if (!key.equals(worldKey) || list == null) {
            worldKey = key;
            list = store.forWorld(key);
        }
        dimension = mc.world.getRegistryKey().getValue().toString();
        distance = Integer.MIN_VALUE;
    }

    private static String worldKey() {
        IntegratedServer server = mc.getServer();
        if (mc.isInSingleplayer() && server != null) {
            Path root = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
            return "world:" + (root.getFileName() == null ? "world" : root.getFileName().toString());
        }
        ServerInfo info = mc.getCurrentServerEntry();
        if (info != null && info.address != null) return "server:" + info.address.trim().toLowerCase(Locale.ROOT);
        return "other";
    }

    private void notify(String message) {
        if (mc.inGameHud != null) mc.inGameHud.setOverlayMessage(Text.literal(message), false);
    }

    public void addHere() {
        if (mc.player == null || mc.world == null) return;
        syncWorld();
        int next = 1;
        for (Waypoint w : list) {
            if (!w.name().startsWith("WP ")) continue;
            try {
                next = Math.max(next, Integer.parseInt(w.name().substring(3).trim()) + 1);
            } catch (NumberFormatException ignored) {
                // a renamed waypoint
            }
        }
        Waypoint w = new Waypoint("WP " + next, MathHelper.floor(mc.player.getX()), MathHelper.floor(mc.player.getY()),
                MathHelper.floor(mc.player.getZ()), dimension);
        list.add(w);
        store.save();
        distance = Integer.MIN_VALUE;
        notify("Saved " + w.name() + " at " + w.x() + " " + w.y() + " " + w.z());
    }

    public void deleteLast() {
        if (mc.world == null) return;
        syncWorld();
        if (list.isEmpty()) {
            notify("No waypoints to delete");
            return;
        }
        Waypoint w = list.remove(list.size() - 1);
        store.save();
        distance = Integer.MIN_VALUE;
        notify("Deleted " + w.name());
    }

    public void clearAll() {
        if (mc.world == null) return;
        syncWorld();
        int n = list.size();
        list.clear();
        store.save();
        distance = Integer.MIN_VALUE;
        notify("Cleared " + n + (n == 1 ? " waypoint" : " waypoints"));
    }

    private void refresh(boolean editing) {
        boolean sharp = dev.astroclient.gui.theme.ThemeSettings.get().sharp();
        if (!due(editing) && sharp == cachedSharp) return;
        String name;
        int dist;
        int dir;
        if (mc.player == null || list == null) {
            cachedVisible = editing;
            name = "Home";
            dist = 128;
            dir = 1;
        } else {
            Waypoint best = null;
            double bestSq = Double.MAX_VALUE;
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            for (int i = 0, n = list.size(); i < n; i++) {
                Waypoint w = list.get(i);
                if (!w.dimension().equals(dimension)) continue;
                double dx = w.x() + 0.5 - px, dy = w.y() - py, dz = w.z() + 0.5 - pz;
                double sq = dx * dx + dy * dy + dz * dz;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = w;
                }
            }
            if (best == null) {
                cachedVisible = editing || !hideEmpty.get();
                name = "WP";
                dist = -1;
                dir = -1;
            } else {
                cachedVisible = true;
                name = best.name();
                dist = (int) Math.round(Math.sqrt(bestSq));
                double dx = best.x() + 0.5 - px, dz = best.z() + 0.5 - pz;
                float yawTo = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float rel = MathHelper.wrapDegrees(yawTo - mc.player.getYaw());
                dir = dist < 2 ? -1 : Math.floorMod(Math.round(rel / 45f), 8);
            }
        }
        boolean changed = sharp != cachedSharp || !name.equals(shownName) || dir != arrow || dist != distance;
        if (dist != distance) shownDistance = dist < 0 ? "None" : dist + "m";
        shownName = name;
        distance = dist;
        arrow = dir;
        cachedSharp = sharp;
        if (changed || cachedWidth == 0) cachedWidth = HudPill.width(shownName, shownDistance) + (arrow >= 0 ? ARROW + ARROW_GAP : 0);
    }

    @Override
    public boolean visible(boolean editing) {
        refresh(editing);
        return cachedVisible;
    }

    @Override
    public int width(boolean editing) {
        refresh(editing);
        return cachedWidth;
    }

    @Override
    public int height(boolean editing) {
        return HudPill.HEIGHT;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        refresh(editing);
        HudPill.draw(context, x, y, shownName, shownDistance, cachedWidth, background.get());
        if (arrow >= 0) Icons.draw(context, Icons.ARROWS[arrow], x + cachedWidth - 6 - ARROW, y + (HudPill.HEIGHT - ARROW) / 2, Theme.ACCENT_2);
    }
}
