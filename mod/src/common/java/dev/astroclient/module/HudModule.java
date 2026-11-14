package dev.astroclient.module;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

/**
 * A module that draws an element on the HUD which the player can drag around in the HUD editor.
 * Positions are stored as fractions of the free space (0 = left/top edge, 1 = right/bottom edge),
 * so elements keep their place at any window size or GUI scale. Elements never moved are stacked
 * in the top-left corner automatically.
 */
public abstract class HudModule extends Module {
    private float fx = Float.NaN;
    private float fy = Float.NaN;
    private long cachedFrame = -1;
    private boolean cachedEditing;
    private long nextRefreshMs;

    protected HudModule(String name, String description) {
        super(name, description, Category.HUD);
    }

    protected HudModule(String name, String description, Category category) {
        super(name, description, category);
    }

    public abstract int width(boolean editing);

    public abstract int height(boolean editing);

    /** @param editing true inside the HUD editor: show sample values when there's nothing to show yet */
    public abstract void render(DrawContext context, int x, int y, boolean editing);

    /** Whether there is anything to draw right now (outside the editor everything is optional). */
    public boolean visible(boolean editing) {
        return true;
    }

    /** False for elements that draw items, which must not be inside a batched GUI draw on 1.21.1. */
    public boolean batchable() {
        return true;
    }

    /**
     * True when cached HUD content should be rebuilt: at most once per rendered frame, and no more than
     * the "HUD refresh" setting allows per second. Width, height and render all read the same cache.
     */
    protected final boolean due(boolean editing) {
        long frame = ModuleManager.frame();
        // the HUD editor may run without a world (no HUD frames), so it only uses the time gate
        if (!editing && frame == cachedFrame && editing == cachedEditing) return false;
        long now = net.minecraft.util.Util.getMeasuringTimeMs();
        int hz = dev.astroclient.gui.theme.ThemeSettings.get().hudRefreshHz;
        boolean first = cachedFrame < 0 || editing != cachedEditing;
        cachedFrame = frame;
        if (!first && hz > 0 && now < nextRefreshMs) return false;
        cachedEditing = editing;
        nextRefreshMs = now + (hz > 0 ? 1000L / hz : 0);
        return true;
    }

    public boolean hasPosition() {
        return !Float.isNaN(fx) && !Float.isNaN(fy);
    }

    public float positionX() {
        return fx;
    }

    public float positionY() {
        return fy;
    }

    public void setPosition(float fx, float fy) {
        this.fx = MathHelper.clamp(fx, 0f, 1f);
        this.fy = MathHelper.clamp(fy, 0f, 1f);
    }

    public void resetPosition() {
        fx = Float.NaN;
        fy = Float.NaN;
    }

    /** Stores a pixel position for an element of size w×h on a screen of size sw×sh. */
    public void setPixelPosition(int x, int y, int w, int h, int sw, int sh) {
        setPosition(sw > w ? (float) x / (sw - w) : 0f, sh > h ? (float) y / (sh - h) : 0f);
    }
}
