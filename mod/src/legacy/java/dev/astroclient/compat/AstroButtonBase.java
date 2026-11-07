package dev.astroclient.compat;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;


/** Version shim (1.21.1): custom-painted button; subclasses implement {@link #renderButton}. */
public abstract class AstroButtonBase extends ButtonWidget {
    protected AstroButtonBase(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }

    protected abstract void renderButton(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        renderButton(context, mouseX, mouseY, delta);
    }
}
