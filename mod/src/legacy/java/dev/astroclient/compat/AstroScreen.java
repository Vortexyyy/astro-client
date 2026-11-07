package dev.astroclient.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Version shim (1.21.1): adapts vanilla input callbacks to one set of methods, so Astro's screens
 * are written once for every Minecraft version. Child widgets (text fields, buttons) get clicks first.
 */
public abstract class AstroScreen extends Screen {
    protected AstroScreen(Text title) {
        super(title);
    }

    protected boolean onMouseClicked(double x, double y, int button) {
        return false;
    }

    protected boolean onMouseReleased(double x, double y, int button) {
        return false;
    }

    protected boolean onMouseDragged(double x, double y, int button) {
        return false;
    }

    protected boolean onKeyPressed(int key, int scancode, int modifiers) {
        return false;
    }

    /** Called before the focused widget gets the character; return true to swallow it. */
    protected boolean onCharTyped(String chars) {
        return false;
    }

    /** Vanilla's menu blur over the world (call at most once per frame). */
    protected void blurWorld(DrawContext context, float delta) {
        applyBlur(delta);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button) || onMouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        boolean handled = onMouseReleased(x, y, button);
        return super.mouseReleased(x, y, button) || handled;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return onMouseDragged(x, y, button) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scancode, int modifiers) {
        return onKeyPressed(key, scancode, modifiers) || super.keyPressed(key, scancode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        return onCharTyped(String.valueOf(chr)) || super.charTyped(chr, modifiers);
    }
}
