package dev.astroclient.gui.title;

import dev.astroclient.compat.AstroButtonBase;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.Icons;
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
    /** the screen's main action: drawn filled with the accent (Orbit) */
    private boolean primary;

    public AstroButton(int x, int y, int width, int height, net.minecraft.text.Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress);
        this.baseY = y;
    }

    public AstroButton leftAligned() {
        this.leftAligned = true;
        return this;
    }

    public AstroButton primary() {
        this.primary = true;
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

        if (ThemeSettings.get().sharp()) {
            renderOrbit(context, x, y, h, panel);
            return;
        }
        // glass surface: the screen's main action carries a violet tint; hover and keyboard focus light the ring
        boolean main = primary && active;
        if (h > 0.01f) RenderUtil.glow(context, x, y, width, height, radius, Theme.withAlpha(Theme.ACCENT, 0.55f * h * alpha), 7);
        int fill = Theme.lerp(main ? Theme.lerp(Theme.PANEL, Theme.ACCENT, 0.24f) : Theme.PANEL, HOVER_BG, h);
        int ring = Theme.lerp(main ? Theme.withAlpha(Theme.ACCENT, 0.7f) : Theme.BORDER_STRONG,
                isFocused() ? Theme.ACCENT_2 : Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.3f), h);
        RenderUtil.surface(context, x, y, width, height, radius, Theme.withAlpha(fill, alpha * Math.max(panel, h)), Theme.withAlpha(ring, alpha));

        if (leftAligned) {
            float barH = (height - 10) * Math.max(main ? 0.6f : 0.35f, h);
            if (h > 0.02f) RenderUtil.glow(context, x + 6, y + height / 2f - barH / 2f, 2, barH, 1, Theme.withAlpha(Theme.ACCENT_2, 0.6f * h * alpha), 3);
            RenderUtil.gradientRect(context, x + 6, y + height / 2f - barH / 2f, 2, barH, 1, Theme.withAlpha(Theme.ACCENT_2, alpha),
                    Theme.withAlpha(Theme.lerp(Theme.ACCENT_2, Theme.ACCENT, 0.4f + 0.6f * h), alpha));
            // chevron slides in from the left on hover (the main action always shows a faint one)
            float chevron = Math.max(h, main ? 0.4f : 0f);
            if (chevron > 0.02f) {
                Icons.draw(context, Icons.RIGHT, x + width - 12 - Math.round(5 * (1f - h)), y + (height - 5) / 2, Theme.withAlpha(Theme.ACCENT_2, alpha * chevron));
            }
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

    /**
     * Orbit button: square, hairline border that turns accent on hover, accent marker and chevron for the
     * side column; the primary action is filled with the accent and uses dark text.
     */
    private void renderOrbit(DrawContext context, int x, int y, float h, float panel) {
        boolean main = primary && active;
        int fill = main ? Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, h * 0.5f) : Theme.lerp(Theme.CARD, Theme.CARD_HOVER, h);
        int border = main ? Theme.ACCENT : Theme.lerp(Theme.BORDER, Theme.ACCENT, h);
        RenderUtil.panel(context, x, y, width, height, 0, Theme.withAlpha(fill, alpha * (main ? 1f : Math.max(panel, h))), Theme.withAlpha(border, alpha));
        int ink = main ? Theme.ON_ACCENT : active ? Theme.lerp(Theme.TEXT, 0xFFFFFFFF, h) : Theme.MUTED;
        var font = MinecraftClient.getInstance().textRenderer;
        if (leftAligned) {
            if (!main) RenderUtil.roundedRect(context, x + 7, y + height / 2f - 1.5f, 3, 3, 0, Theme.withAlpha(Theme.lerp(Theme.BORDER_STRONG, Theme.ACCENT, h), alpha));
            Icons.draw(context, Icons.RIGHT, x + width - 12 + Math.round(2 * h), y + (height - 5) / 2,
                    Theme.withAlpha(main ? Theme.ON_ACCENT : Theme.lerp(Theme.BORDER_STRONG, Theme.ACCENT, h), alpha));
            if (Theme.visible(alpha)) context.drawText(font, getMessage(), x + (main ? 9 : 15) + Math.round(2 * h), y + (height - 8) / 2, Theme.withAlpha(ink, alpha), false);
        } else if (Theme.visible(alpha)) {
            int tw = font.getWidth(getMessage());
            context.drawText(font, getMessage(), x + (width - tw) / 2, y + (height - 8) / 2, Theme.withAlpha(ink, alpha), false);
        }
    }
}
