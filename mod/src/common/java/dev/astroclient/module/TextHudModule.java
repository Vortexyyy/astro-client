package dev.astroclient.module;

import dev.astroclient.gui.render.HudPill;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

/** A HUD element that is one line of text (an optional muted label plus a value) in the standard Astro chip. */
public abstract class TextHudModule extends HudModule {
    protected final BooleanSetting background = add(new BooleanSetting("Background", true));

    private String cachedText = "";
    private boolean cachedVisible;
    private int cachedWidth;
    private boolean cachedSharp;

    protected TextHudModule(String name, String description) {
        super(name, description);
    }

    /** The value to show, or null when there's nothing to show (only outside the editor). */
    protected abstract String text(boolean editing);

    /** Short caps label drawn muted before the value (e.g. "FPS"), or null for none. */
    protected String label() {
        return null;
    }

    /** Rebuilds the text at most at the HUD refresh rate instead of three times every frame. */
    private void refresh(boolean editing) {
        boolean sharp = dev.astroclient.gui.theme.ThemeSettings.get().sharp();
        if (!due(editing) && sharp == cachedSharp) return;
        String t = text(editing);
        cachedVisible = t != null;
        cachedText = t == null ? "" : t;
        cachedSharp = sharp;
        cachedWidth = HudPill.width(label(), cachedText);
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
        HudPill.draw(context, x, y, label(), cachedText, cachedWidth, background.get());
    }
}
