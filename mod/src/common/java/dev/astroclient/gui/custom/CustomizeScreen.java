package dev.astroclient.gui.custom;

import dev.astroclient.compat.AstroScreen;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.render.Ui;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.title.AstroButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Menu customization: colour theme, background pictures per screen, button style, panel opacity.
 * Changes apply live (the screen behind is a preview) and are saved when you press Done.
 */
public class CustomizeScreen extends AstroScreen {
    private static final int W = 300;
    private static final int ROW_H = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final List<AstroButton> rows = new ArrayList<>();
    private final List<Supplier<String>> labels = new ArrayList<>();
    private int px;
    private int py;
    private int ph;

    public CustomizeScreen(Screen parent) {
        super(Text.literal("Customize"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        labels.clear();
        ThemeSettings t = ThemeSettings.get();
        int count = 9;
        ph = 40 + count * (ROW_H + GAP) + 34;
        px = (width - W) / 2;
        py = Math.max(10, (height - ph) / 2);
        int y = py + 34;

        y = row(y, () -> "Theme: " + t.preset.label, () -> t.preset = next(Theme.Preset.values(), t.preset));
        y = row(y, () -> "Main menu: " + Backgrounds.label(t.menuBackground),
                () -> t.menuBackground = nextBackground(t.menuBackground, false));
        y = row(y, () -> "Singleplayer: " + Backgrounds.label(t.worldsBackground),
                () -> t.worldsBackground = nextBackground(t.worldsBackground, true));
        y = row(y, () -> "Multiplayer: " + Backgrounds.label(t.serversBackground),
                () -> t.serversBackground = nextBackground(t.serversBackground, true));
        y = row(y, () -> "Picture dim: " + t.backgroundDim + "%", () -> t.backgroundDim = (t.backgroundDim + 15) % 90);
        y = row(y, () -> "Panel opacity: " + t.panelOpacity + "%", () -> t.panelOpacity = t.panelOpacity >= 100 ? 40 : t.panelOpacity + 10);
        y = row(y, () -> "Buttons: " + t.buttonStyle.label, () -> t.buttonStyle = next(ThemeSettings.ButtonStyle.values(), t.buttonStyle));
        y = row(y, () -> "Player model: " + (t.showPlayer ? "On" : "Off"), () -> t.showPlayer = !t.showPlayer);
        y = row(y, () -> "Astro menus: " + (t.customMenus ? "On" : "Off (vanilla)"), () -> t.customMenus = !t.customMenus);

        int half = (W - 24 - 6) / 2;
        addDrawableChild(new AstroButton(px + 12, y + 6, half, ROW_H, Text.literal("Open pictures folder"), b -> Backgrounds.openFolder()));
        addDrawableChild(new AstroButton(px + 12 + half + 6, y + 6, half, ROW_H, Text.literal("Done"), b -> close()));
    }

    private int row(int y, Supplier<String> label, Runnable cycle) {
        AstroButton button = new AstroButton(px + 12, y, W - 24, ROW_H, Text.literal(label.get()), b -> {
            cycle.run();
            ThemeSettings.save(); // applies the accent colours immediately
            refreshLabels();
        }).leftAligned();
        rows.add(button);
        labels.add(label);
        addDrawableChild(button);
        return y + ROW_H + GAP;
    }

    private void refreshLabels() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).setMessage(Text.literal(labels.get(i).get()));
    }

    private static <T> T next(T[] values, T current) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    /** Cycles Starfield → each picture in the folder → ("Same as main menu" for sub-screens). */
    private static String nextBackground(String current, boolean allowSame) {
        List<String> options = new ArrayList<>();
        if (allowSame) options.add(ThemeSettings.SAME);
        options.add(ThemeSettings.STARFIELD);
        options.addAll(Backgrounds.list());
        int i = options.indexOf(current);
        return options.get((i + 1) % options.size());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        String hint = Backgrounds.list().isEmpty()
                ? "Add PNG pictures to the pictures folder to use them as backgrounds."
                : Backgrounds.list().size() + " picture(s) found in config/astroclient/backgrounds";
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, Math.min(height - 12, py + ph + 6), Theme.MUTED);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Backgrounds.draw(context, ThemeSettings.get().menuBackground, width, height);
        Ui.panel(context, px, py, W, ph);
        context.drawText(textRenderer, "Customize Astro", px + 14, py + 13, 0xFFFFFFFF, false);
        Ui.divider(context, px + 12, py + 27, px + W - 12);
    }

    @Override
    protected boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        ThemeSettings.save();
        Backgrounds.reload();
        client.setScreen(parent);
    }
}
