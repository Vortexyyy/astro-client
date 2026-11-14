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
 * Menu customization: colour theme, background pictures per screen, button style, panel opacity, glow
 * strength and animation speed. Changes apply live (the screen behind is a preview) and are saved when
 * you press Done. The options sit in one column when the window is tall enough, otherwise in two.
 */
public class CustomizeScreen extends AstroScreen {
    private static final int ROWS = 13;

    private final Screen parent;
    private final List<AstroButton> rows = new ArrayList<>();
    private final List<Supplier<String>> labels = new ArrayList<>();
    private int px;
    private int py;
    private int pw;
    private int ph;
    // row layout for this window size
    private int rowH;
    private int gap;
    private int perColumn;
    private int columnW;
    private int placed;

    public CustomizeScreen(Screen parent) {
        super(Text.literal("Customize"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        labels.clear();
        ThemeSettings t = ThemeSettings.get();
        // one column when it fits; two (and, on very short windows, slimmer rows) when it doesn't
        rowH = 20;
        gap = 4;
        int columns = 40 + ROWS * (rowH + gap) + 34 <= height - 16 ? 1 : 2;
        perColumn = (ROWS + columns - 1) / columns;
        if (40 + perColumn * (rowH + gap) + 34 > height - 22) {
            rowH = 18;
            gap = 3;
        }
        ph = 40 + perColumn * (rowH + gap) + 34;
        pw = columns == 1 ? 300 : Math.min(width - 16, 470);
        columnW = (pw - 24 - (columns - 1) * 6) / columns;
        px = (width - pw) / 2;
        py = Math.max(3, (height - ph - 12) / 2); // the hint line sits under the panel
        placed = 0;

        // look
        row(() -> "Theme: " + t.preset.label, () -> t.preset = next(Theme.Preset.values(), t.preset));
        row(() -> "Corners: " + t.buttonStyle.label, () -> t.buttonStyle = next(ThemeSettings.ButtonStyle.values(), t.buttonStyle));
        row(() -> "Accent glow: " + (t.glow == 0 ? "Off" : t.glow + "%"), () -> t.glow = t.glow >= 150 ? 0 : t.glow + 50);
        row(() -> "Animations: " + t.motion.label, () -> t.motion = next(ThemeSettings.Motion.values(), t.motion));
        row(() -> "Effects quality: " + t.effects.label, () -> t.effects = next(ThemeSettings.Quality.values(), t.effects));
        row(() -> "Panel opacity: " + t.panelOpacity + "%", () -> t.panelOpacity = t.panelOpacity >= 100 ? 40 : t.panelOpacity + 10);
        row(() -> "Player model: " + (t.showPlayer ? "On" : "Off"), () -> t.showPlayer = !t.showPlayer);
        // backgrounds and behaviour
        row(() -> "Main menu: " + Backgrounds.label(t.menuBackground),
                () -> t.menuBackground = nextBackground(t.menuBackground, false));
        row(() -> "Singleplayer: " + Backgrounds.label(t.worldsBackground),
                () -> t.worldsBackground = nextBackground(t.worldsBackground, true));
        row(() -> "Multiplayer: " + Backgrounds.label(t.serversBackground),
                () -> t.serversBackground = nextBackground(t.serversBackground, true));
        row(() -> "Picture dim: " + t.backgroundDim + "%", () -> t.backgroundDim = (t.backgroundDim + 15) % 90);
        row(() -> "Astro menus: " + (t.customMenus ? "On" : "Off (vanilla)"), () -> t.customMenus = !t.customMenus);
        row(() -> "HUD refresh: " + (t.hudRefreshHz == 0 ? "Every frame" : t.hudRefreshHz + " / sec"), () -> t.hudRefreshHz = nextRefresh(t.hudRefreshHz));

        int y = py + 34 + perColumn * (rowH + gap) + 6;
        int half = (pw - 24 - 6) / 2;
        addDrawableChild(new AstroButton(px + 12, y, half, rowH, Text.literal("Open pictures folder"), b -> Backgrounds.openFolder()));
        addDrawableChild(new AstroButton(px + 12 + half + 6, y, half, rowH, Text.literal("Done"), b -> close()).primary());
    }

    /** 10, 20, 30, 60 updates a second, then every frame. */
    private static int nextRefresh(int hz) {
        return switch (hz) {
            case 10 -> 20;
            case 20 -> 30;
            case 30 -> 60;
            case 60 -> 0;
            default -> 10;
        };
    }

    private void row(Supplier<String> label, Runnable cycle) {
        int x = px + 12 + (placed / perColumn) * (columnW + 6);
        int y = py + 34 + (placed % perColumn) * (rowH + gap);
        placed++;
        AstroButton button = new AstroButton(x, y, columnW, rowH, Text.literal(label.get()), b -> {
            cycle.run();
            ThemeSettings.save(); // applies the accent colours immediately
            refreshLabels();
        }).leftAligned();
        rows.add(button);
        labels.add(label);
        addDrawableChild(button);
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
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, Math.min(height - 9, py + ph + 5), Theme.MUTED);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Backgrounds.draw(context, ThemeSettings.get().menuBackground, width, height);
        Ui.panel(context, px, py, pw, ph);
        context.drawText(textRenderer, "Customize Astro", px + 14, py + 13, 0xFFFFFFFF, false);
        String note = "Changes apply live";
        if (pw > 260) context.drawText(textRenderer, note, px + pw - 14 - textRenderer.getWidth(note), py + 13, Theme.MUTED, false);
        Ui.divider(context, px + 12, py + 27, px + pw - 12);
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
