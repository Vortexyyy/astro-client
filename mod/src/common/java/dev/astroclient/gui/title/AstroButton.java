package dev.astroclient.gui.title;

import dev.astroclient.compat.AstroButtonBase;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.theme.ThemeSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Glowing menu button in the player's chosen style (rounded, pill or sharp).
 * Extends ButtonWidget so narration, focus and keyboard input keep working.
 */
public class AstroButton extends AstroButtonBase {
    private static final int HOVER_BG = 0xFF2A1F5C;

    private final Animation hover = new Animation(0f, 14f);
    private int baseY;
    /** left-aligned label with an accent bar, for the main menu's side column */
    private boolean leftAligned;

    public AstroButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress);
        this.baseY = y;
    }

    public AstroButton leftAligned() {
        this.leftAligned = true;
        return this;
    }

    /** Used by staggered intros: slide from {@code offset} px while fading in. */
    public void setIntro(float offset, float alpha) {
        setY(Math.round(baseY + offset));
        setAlpha(alpha);
    }

    public void moveTo(int x, int y) {
        setX(x);
        setY(y);
        baseY = y;
    }

    @Override
    protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        hover.setTarget(active && isSelected() ? 1f : 0f);
        float h = hover.update();
        int x = getX();
        int y = getY();
        float radius = ThemeSettings.get().buttonRadius(height);
        float panel = ThemeSettings.get().panelAlpha();

        if (h > 0.01f) RenderUtil.glow(context, x, y, width, height, radius, Theme.withAlpha(Theme.ACCENT, 0.55f * h * alpha), 6);
        RenderUtil.roundedRect(context, x, y, width, height, radius,
                Theme.withAlpha(Theme.lerp(Theme.PANEL, HOVER_BG, h), alpha * Math.max(panel, h)));

        if (leftAligned) {
            float barH = (height - 10) * Math.max(0.35f, h);
            RenderUtil.roundedRect(context, x + 6, y + height / 2f - barH / 2f, 2, barH, 1, Theme.withAlpha(Theme.ACCENT_2, alpha));
        } else {
            // neon underline grows from the centre on hover
            float lineW = (width - radius * 2) * h;
            if (lineW > 1f) {
                RenderUtil.roundedRect(context, x + width / 2f - lineW / 2f, y + height - 2, lineW, 1.5f, 0.75f,
                        Theme.withAlpha(Theme.ACCENT_2, alpha));
            }
        }

        if (Theme.visible(alpha)) {
            int color = Theme.withAlpha(active ? Theme.lerp(Theme.TEXT, 0xFFFFFFFF, h) : Theme.MUTED, alpha);
            var font = MinecraftClient.getInstance().textRenderer;
            if (leftAligned) context.drawTextWithShadow(font, getMessage(), x + 14 + Math.round(3 * h), y + (height - 8) / 2, color);
            else context.drawCenteredTextWithShadow(font, getMessage(), x + width / 2, y + (height - 8) / 2, color);
        }
    }
}
