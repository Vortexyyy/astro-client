package dev.astroclient.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;


/**
 * Version shim (1.21.11): ButtonWidget.renderWidget is final and paints through drawIcon,
 * so the whole custom button is drawn there.
 */
public abstract class AstroButtonBase extends ButtonWidget {
    protected AstroButtonBase(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    protected abstract void renderButton(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    protected void drawIcon(DrawContext context, int mouseX, int mouseY, float delta) {
        renderButton(context, mouseX, mouseY, delta);
    }
}
