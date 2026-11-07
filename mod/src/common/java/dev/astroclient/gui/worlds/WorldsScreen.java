package dev.astroclient.gui.worlds;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.compat.Gfx;
import dev.astroclient.compat.Platform;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.render.Ui;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.title.AstroButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.level.storage.LevelSummary;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Astro's Singleplayer menu: world cards with icon, last played and game mode. Click a card to play.
 * Editing, deleting and backups stay in vanilla's list ("Advanced…").
 */
public class WorldsScreen extends AstroScreen {
    private static final int MAX_W = 520;
    private static final int HEADER = 40;
    private static final int CARD_H = 44;
    private static final int GAP = 6;
    private static final int PAD = 12;
    private static final int ICON = 32;
    private static final DateFormat DATE = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    private final Screen parent;
    private final Animation scroll = new Animation(0f, 18f);
    private final Map<String, Animation> hovers = new HashMap<>();
    private final Map<String, Identifier> icons = new HashMap<>();
    private List<LevelSummary> worlds;
    private String loadError;
    private TextFieldWidget search;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public WorldsScreen(Screen parent) {
        super(Text.translatable("selectWorld.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 32, MAX_W);
        ph = height - 48;
        px = (width - pw) / 2;
        py = 24;

        String previous = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, px + PAD + 4, py + PAD + 4, 150, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search worlds…").formatted(Formatting.DARK_GRAY));
        search.setText(previous);
        search.setChangedListener(s -> scroll.setTarget(0f));
        addSelectableChild(search);

        int bx = px + pw - PAD;
        bx = button(bx, "Back", 50, () -> close());
        bx = button(bx, "Advanced…", 70, () -> client.setScreen(new SelectWorldScreen(this)));
        button(bx, "Create world", 84, () -> Platform.openCreateWorld(this));

        if (worlds == null) loadWorlds();
    }

    private int button(int right, String label, int w, Runnable action) {
        addDrawableChild(new AstroButton(right - w, py + PAD, w, 20, Text.literal(label), b -> action.run()));
        return right - w - 6;
    }

    private void loadWorlds() {
        try {
            var storage = client.getLevelStorage();
            storage.loadSummaries(storage.getLevelList()).whenComplete((list, error) -> client.execute(() -> {
                if (error != null) loadError = "Could not read worlds: " + error.getMessage();
                else worlds = list.stream().sorted((a, b) -> Long.compare(b.getLastPlayed(), a.getLastPlayed())).toList();
            }));
        } catch (Exception e) {
            loadError = "Could not read worlds: " + e.getMessage();
        }
    }

    private List<LevelSummary> visible() {
        if (worlds == null) return List.of();
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return worlds;
        List<LevelSummary> out = new ArrayList<>();
        for (LevelSummary w : worlds) {
            if (w.getDisplayName().toLowerCase(Locale.ROOT).contains(q) || w.getName().toLowerCase(Locale.ROOT).contains(q)) out.add(w);
        }
        return out;
    }

    private int listTop() {
        return py + HEADER + 8;
    }

    private int listHeight() {
        return py + ph - PAD - listTop();
    }

    private float maxScroll(int count) {
        return Math.max(0, count * (CARD_H + GAP) - GAP - listHeight());
    }

    private Identifier icon(LevelSummary world) {
        return icons.computeIfAbsent(world.getName(), name -> {
            Path path = world.getIconPath();
            if (path == null || !Files.isRegularFile(path)) return null;
            try (InputStream in = Files.newInputStream(path)) {
                NativeImage image = NativeImage.read(in);
                Identifier id = Identifier.of("astroclient", "world/" + Integer.toHexString(name.hashCode()));
                Platform.registerTexture(id, image);
                return id;
            } catch (Exception e) {
                AstroClient.LOGGER.debug("No icon for world {}", name, e);
                return null;
            }
        });
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // search box
        RenderUtil.roundedRect(context, px + PAD, py + PAD, 162, 20, 5, search.isFocused() ? Ui.CARD_HOVER : Ui.CARD);
        search.render(context, mouseX, mouseY, delta);
        Ui.divider(context, px + PAD, py + HEADER, px + pw - PAD);

        List<LevelSummary> list = visible();
        scroll.setTarget(MathHelper.clamp(scroll.getTarget(), 0f, maxScroll(list.size())));
        float offset = scroll.update();
        int top = listTop();
        int h = listHeight();

        if (worlds == null || list.isEmpty()) {
            String message = loadError != null ? loadError
                    : worlds == null ? "Loading worlds…"
                    : worlds.isEmpty() ? "No worlds yet. Click Create world to start one." : "No worlds match your search.";
            context.drawCenteredTextWithShadow(textRenderer, message, px + pw / 2, top + 30, Theme.MUTED);
            return;
        }

        context.enableScissor(px + 2, top, px + pw - 2, top + h);
        for (int i = 0; i < list.size(); i++) {
            int y = top + Math.round(i * (CARD_H + GAP) - offset);
            if (y + CARD_H < top || y > top + h) continue;
            renderCard(context, list.get(i), px + PAD, y, pw - PAD * 2, mouseX, mouseY, Ui.inside(mouseX, mouseY, px, top, pw, h));
        }
        context.disableScissor();
    }

    private void renderCard(DrawContext context, LevelSummary world, int x, int y, int w, int mouseX, int mouseY, boolean inList) {
        boolean hovered = inList && Ui.inside(mouseX, mouseY, x, y, w, CARD_H);
        Animation hover = hovers.computeIfAbsent(world.getName(), n -> new Animation(0f, 16f));
        hover.setTarget(hovered ? 1f : 0f);
        float hv = hover.update();

        if (hv > 0.01f) RenderUtil.glow(context, x, y, w, CARD_H, 8, Theme.withAlpha(Theme.ACCENT, 0.35f * hv), 5);
        RenderUtil.roundedRect(context, x, y, w, CARD_H, 8, Theme.lerp(Ui.CARD, Ui.CARD_HOVER, hv));

        int iconX = x + 6;
        int iconY = y + (CARD_H - ICON) / 2;
        Identifier icon = icon(world);
        if (icon != null) {
            Gfx.push(context);
            Gfx.translate(context, iconX, iconY);
            Gfx.scale(context, ICON / 64f, ICON / 64f);
            Gfx.texture(context, icon, 0, 0, 0, 0, 64, 64, 64, 64);
            Gfx.pop(context);
        } else {
            Ui.initialTile(context, textRenderer, world.getDisplayName(), iconX, iconY, ICON);
        }

        int tx = iconX + ICON + 10;
        String name = textRenderer.trimToWidth(world.getDisplayName(), w - ICON - 120);
        context.drawText(textRenderer, name, tx, y + 9, 0xFFFFFFFF, false);
        String mode = world.isHardcore() ? "Hardcore" : capitalize(world.getGameMode().asString());
        String detail = mode + (world.hasCheats() ? " · Cheats" : "") + " · " + DATE.format(new Date(world.getLastPlayed()));
        context.drawText(textRenderer, textRenderer.trimToWidth(detail, w - ICON - 30), tx, y + 23, Theme.MUTED, false);

        String version = world.getVersion().getString();
        context.drawText(textRenderer, version, x + w - 10 - textRenderer.getWidth(version), y + 9, Theme.withAlpha(Theme.ACCENT_2, 0.9f), false);
        if (hv > 0.05f) {
            String play = "▶ Play";
            context.drawText(textRenderer, play, x + w - 10 - textRenderer.getWidth(play), y + 23, Theme.withAlpha(0xFFFFFFFF, hv), false);
        }
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        ThemeSettings theme = ThemeSettings.get();
        Backgrounds.draw(context, theme.resolve(theme.worldsBackground), width, height);
        Ui.panel(context, px, py, pw, ph);
        context.drawText(textRenderer, "Singleplayer", px + PAD + 180, py + PAD + 6, 0xFFFFFFFF, false);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (Ui.inside(mouseX, mouseY, px + PAD, py + PAD, 162, 20)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        int top = listTop();
        if (button != 0 || !Ui.inside(mouseX, mouseY, px, top, pw, listHeight())) return false;
        List<LevelSummary> list = visible();
        int index = (int) ((mouseY - top + scroll.get()) / (CARD_H + GAP));
        if (index < 0 || index >= list.size()) return false;
        play(list.get(index));
        return true;
    }

    private void play(LevelSummary world) {
        // Worlds needing an upgrade, locked or from a newer version go through vanilla's checks and prompts.
        if (!world.isSelectable() || world.requiresConversion() || world.isLocked() || world.isUnavailable()) {
            client.setScreen(new SelectWorldScreen(this));
            return;
        }
        client.createIntegratedServerLoader().start(world.getName(), () -> client.setScreen(this));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll.setTarget(MathHelper.clamp(scroll.getTarget() - (float) verticalAmount * 30f, 0f, maxScroll(visible().size())));
        return true;
    }

    @Override
    protected boolean onCharTyped(String chars) {
        if (!search.isFocused()) {
            setFocused(search);
            search.setFocused(true);
        }
        return false;
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
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        for (Identifier id : icons.values()) if (id != null) client.getTextureManager().destroyTexture(id);
        icons.clear();
        worlds = null; // re-read when we come back (e.g. after creating a world)
    }
}
