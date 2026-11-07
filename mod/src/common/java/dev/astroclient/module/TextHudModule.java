package dev.astroclient.module;

import dev.astroclient.gui.render.HudPill;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;

/** A HUD element that is one line of text in the standard Astro pill. */
public abstract class TextHudModule extends HudModule {
    protected final BooleanSetting background = add(new BooleanSetting("Background", true));

    protected TextHudModule(String name, String description) {
        super(name, description);
    }

    /** The text to show, or null when there's nothing to show (only outside the editor). */
    protected abstract String text(boolean editing);

    private String safeText(boolean editing) {
        String t = text(editing);
        return t == null ? "" : t;
    }

    @Override
    public boolean visible(boolean editing) {
        return text(editing) != null;
    }

    @Override
    public int width(boolean editing) {
        return HudPill.width(safeText(editing));
    }

    @Override
    public int height(boolean editing) {
        return HudPill.HEIGHT;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        HudPill.draw(context, x, y, safeText(editing), background.get());
    }
}
