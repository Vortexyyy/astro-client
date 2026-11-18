package dev.astroclient.module;

import dev.astroclient.bench.Benchmark;
import dev.astroclient.compat.Gfx;
import dev.astroclient.gui.hud.HudEditorScreen;
import dev.astroclient.module.impl.ArmorStatus;
import dev.astroclient.module.impl.ComboDisplay;
import dev.astroclient.module.impl.ItemCounter;
import dev.astroclient.module.impl.Waypoints;
import dev.astroclient.module.impl.CpsCounter;
import dev.astroclient.module.impl.Coordinates;
import dev.astroclient.module.impl.Crosshair;
import dev.astroclient.module.impl.FpsDisplay;
import dev.astroclient.module.impl.Freelook;
import dev.astroclient.module.impl.Fullbright;
import dev.astroclient.module.impl.Keystrokes;
import dev.astroclient.module.impl.PingDisplay;
import dev.astroclient.module.impl.PotionEffects;
import dev.astroclient.module.impl.ToggleSprint;
import dev.astroclient.module.impl.Zoom;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private static final int HUD_MARGIN = 6;
    private static final int HUD_GAP = 4;

    private final List<Module> modules = new ArrayList<>();
    private final List<Placed> placed = new ArrayList<>();
    private CpsCounter cps;
    private Crosshair crosshair;
    private static long frame;

    /** Rendered-frame counter, so HUD modules compute their content once per frame. */
    public static long frame() {
        return frame;
    }

    /** Where a HUD element is drawn this frame. */
    public record Placed(HudModule module, int x, int y, int w, int h) {}

    public void registerDefaults() {
        register(new ToggleSprint());
        register(new Freelook());
        register(new Zoom());
        register(new Fullbright());
        register(new Crosshair());
        register(new FpsDisplay());
        register(new CpsCounter());
        register(new Coordinates());
        register(new PingDisplay());
        register(new Keystrokes());
        register(new ArmorStatus());
        register(new PotionEffects());
        register(new ComboDisplay());
        register(new ItemCounter());
        register(new Waypoints());
        cps = get(CpsCounter.class);
        crosshair = get(Crosshair.class);
    }

    public void register(Module module) {
        modules.add(module);
    }

    public List<Module> all() {
        return Collections.unmodifiableList(modules);
    }

    public <T extends Module> T get(Class<T> type) {
        for (Module m : modules) if (type.isInstance(m)) return type.cast(m);
        return null;
    }

    public List<Module> byCategory(Category category) {
        return modules.stream().filter(m -> m.getCategory() == category).toList();
    }

    public void tick(MinecraftClient client) {
        if (client.player == null) return;
        for (Module m : modules) if (m.isEnabled()) m.onTick();
    }

    /**
     * Positions of all enabled HUD elements on a screen of the given size: saved positions where the
     * player moved them, otherwise stacked down the top-left corner.
     */
    public List<Placed> layout(int screenW, int screenH, boolean editing) {
        List<Placed> out = new ArrayList<>();
        layoutInto(out, screenW, screenH, editing);
        return out;
    }

    private void layoutInto(List<Placed> out, int screenW, int screenH, boolean editing) {
        out.clear();
        int autoY = HUD_MARGIN;
        for (Module m : modules) {
            if (!(m instanceof HudModule hud) || !hud.isEnabled() || !hud.visible(editing)) continue;
            int w = hud.width(editing);
            int h = hud.height(editing);
            if (hud.hasPosition()) {
                out.add(new Placed(hud, Math.round(hud.positionX() * Math.max(0, screenW - w)), Math.round(hud.positionY() * Math.max(0, screenH - h)), w, h));
            } else {
                out.add(new Placed(hud, HUD_MARGIN, autoY, w, h));
                autoY += h + HUD_GAP;
            }
        }
    }

    public void renderHud(DrawContext context) {
        Benchmark.frameStart();
        try {
            renderHudInner(context);
        } finally {
            Benchmark.hudEnd();
        }
    }

    private void renderHudInner(DrawContext context) {
        frame++;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) return;
        if (cps != null && cps.isEnabled()) cps.sample();
        if (crosshair != null && crosshair.isEnabled()) crosshair.draw(context);

        // the HUD editor draws its own copy of the HUD
        if (client.getDebugHud().shouldShowDebugHud() || client.currentScreen instanceof HudEditorScreen) return;
        layoutInto(placed, context.getScaledWindowWidth(), context.getScaledWindowHeight(), false);
        // one batched draw for the text/shape elements instead of one per shape (big FPS win on 1.21.1)
        Gfx.batch(context, () -> {
            for (Placed p : placed) if (p.module().batchable()) p.module().render(context, p.x(), p.y(), false);
        });
        for (Placed p : placed) if (!p.module().batchable()) p.module().render(context, p.x(), p.y(), false);
    }
}
