package dev.astroclient.compat;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Version shim (1.21.9+): vanilla input now arrives as Click/KeyInput/CharInput records; this
 * unpacks them into the same methods the 1.21.1 shim exposes. Child widgets get clicks first.
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
        applyBlur(context);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return super.mouseClicked(click, doubled) || onMouseClicked(click.x(), click.y(), click.button());
    }

    @Override
    public boolean mouseReleased(Click click) {
        boolean handled = onMouseReleased(click.x(), click.y(), click.button());
        return super.mouseReleased(click) || handled;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        return onMouseDragged(click.x(), click.y(), click.button()) || super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        return onKeyPressed(input.key(), input.scancode(), input.modifiers()) || super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharInput input) {
        return (input.isValidChar() && onCharTyped(input.asString())) || super.charTyped(input);
    }
}
