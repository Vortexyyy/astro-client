package dev.astroclient.gui.menu;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.compat.Keys;
import dev.astroclient.gui.hud.HudEditorScreen;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.Icons;
import dev.astroclient.gui.render.PixelFont;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.ModeSetting;
import dev.astroclient.module.setting.NumberSetting;
import dev.astroclient.module.setting.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Right Shift mod menu: a sidebar of categories next to a searchable grid of module cards.
 * Clicking a card toggles it; the gear (or right-click) opens that module's settings page, the star
 * (or middle-click) adds it to Favourites. Settings pages have a Reset button for the module's defaults.
 * Keyboard: type to search, Enter toggles the top match, Tab/Shift+Tab switch category, Esc goes back.
 */
public class ModMenuScreen extends AstroScreen {
    private static final int MAX_W = 540;
    private static final int MAX_H = 320;
    private static final int SIDE = 118;
    private static final int HEADER = 34;
    private static final int FOOTER = 18;
    private static final int PAD = 10;
    private static final int GAP = 6;
    private static final int CAT_H = 20;
    private static final int CARD_H = 50;
    private static final int MIN_CARD_W = 118;
    private static final int RADIUS = 9;
    private static final int SETTINGS_TOP = 16;
    private static final int CARD_BG = Theme.CARD;
    private static final int CARD_HOVER = Theme.CARD_HOVER;
    private static final int OFF_BAR = Theme.TRACK;
    private static final int BORDER = Theme.BORDER;
    /** Opaque stand-ins for the translucent hairlines, for shapes that are layered (ring under fill). */
    private static final int DEEP = Theme.PANEL_DEEP | 0xFF000000;
    private static final int RING_IDLE = Theme.lerp(DEEP, 0xFFA096FF, 0.2f);

    // -- Tunables: animation speeds (higher = snappier; ~8 settles in about half a second) --
    private static final float OPEN_SPEED = 12f;
    private static final float HOVER_SPEED = 16f;
    private static final float TOGGLE_SPEED = 12f;
    private static final float SCROLL_SPEED = 18f;
    /** content fade/slide when switching category or opening a settings page */
    private static final float PAGE_SPEED = 9f;
    /** glow pulse on a card when its mod is toggled */
    private static final float PULSE_SPEED = 5f;
    /** GUI pixels scrolled per mouse-wheel notch */
    private static final float SCROLL_STEP = 24f;

    /** A sidebar entry: every mod, the pinned ones, or one category. */
    private record Tab(Category category, boolean favourites) {
        String label() {
            return favourites ? "Favourites" : category == null ? "All" : category.displayName();
        }

        String title() {
            return favourites ? "Favourites" : category == null ? "All mods" : category.displayName();
        }

        String[] icon() {
            return favourites ? Icons.STAR : Icons.forCategory(category);
        }

        boolean holds(Module m) {
            return favourites ? m.isPinned() : category == null || m.getCategory() == category;
        }
    }

    private static final Tab ALL = new Tab(null, false);
    private static final Tab FAVOURITES = new Tab(null, true);

    /** Tab survives closing and reopening the menu. */
    private static Tab lastTab = ALL;

    private final Animation open = new Animation(0f, OPEN_SPEED);
    private final Animation scroll = new Animation(0f, SCROLL_SPEED);
    private final Animation settingsScroll = new Animation(0f, SCROLL_SPEED);
    private final Animation page = new Animation(0f, PAGE_SPEED);
    private final Animation selector = new Animation(0f, 14f);
    private final Map<Object, Animation> anims = new HashMap<>();
    private final Map<Module, Boolean> lastEnabled = new HashMap<>();
    /** Favourites first; fixed while a tab is open so a card doesn't jump away when it is starred. */
    private List<Module> order = List.of();
    private TextFieldWidget search;
    private Tab tab = lastTab;
    private Module settingsFor;
    private Module pressed;
    private boolean closing;
    private NumberSetting activeSlider;
    private int sliderX;
    private int sliderW;
    /** what the pointer is over this frame, explained in the footer */
    private String hoverHint;

    // panel geometry for the current frame
    private int px;
    private int py;
    private int pw;
    private int ph;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    public ModMenuScreen() {
        super(Text.literal("Astro Client"));
        open.setTarget(1f);
        page.setTarget(1f);
        reorder();
    }

    /** Opens straight on a module's settings page (used by the screenshot benchmark). */
    public ModMenuScreen showSettings(String moduleName) {
        for (Module m : AstroClient.get().modules().all()) if (m.getName().equals(moduleName)) settingsFor = m;
        return this;
    }

    /** Replays the content entrance: cards (or settings rows) fade and slide in. */
    private void pageIn() {
        page.snap(0f);
        page.setTarget(1f);
    }

    private void reorder() {
        List<Module> all = AstroClient.get().modules().all();
        List<Module> out = new ArrayList<>(all.size());
        for (Module m : all) if (m.isPinned()) out.add(m);
        for (Module m : all) if (!m.isPinned()) out.add(m);
        order = out;
    }

    @Override
    protected void init() {
        String previous = search != null ? search.getText() : "";
        search = new TextFieldWidget(textRenderer, 0, 0, 120, 12, Text.literal("Search"));
        search.setDrawsBackground(false);
        search.setPlaceholder(Text.literal("Search mods…").formatted(Formatting.DARK_GRAY));
        search.setText(previous);
        search.setChangedListener(text -> scroll.setTarget(0f));
        addSelectableChild(search);
        layout(0);
    }

    private void layout(int slide) {
        pw = Math.min(width - 32, MAX_W);
        ph = Math.min(height - 32, MAX_H);
        px = (width - pw) / 2;
        py = (height - ph) / 2 + slide;
        Rect box = searchBox();
        search.setX(box.x + 17);
        search.setY(box.y + 5);
        search.setWidth(box.w - 21);
    }

    private static boolean sharp() {
        return ThemeSettings.get().sharp();
    }

    private static int accent2() {
        return sharp() ? Theme.ACCENT : Theme.ACCENT_2;
    }

    private Animation anim(Object key, String kind, float initial, float speed) {
        return anims.computeIfAbsent(List.of(key, kind), k -> new Animation(initial, speed));
    }

    // ── Data ────────────────────────────────────────────────────────────

    private List<Tab> tabs() {
        List<Tab> list = new ArrayList<>();
        list.add(ALL);
        list.add(FAVOURITES);
        for (Category c : Category.values()) if (!AstroClient.get().modules().byCategory(c).isEmpty()) list.add(new Tab(c, false));
        return list;
    }

    private List<Module> inTab(Tab t) {
        List<Module> out = new ArrayList<>();
        for (Module m : order) if (t.holds(m)) out.add(m);
        return out;
    }

    private String query() {
        return search.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<Module> visibleModules() {
        String q = query();
        if (q.isEmpty()) return inTab(tab);
        List<Module> out = new ArrayList<>();
        // searching looks through every category, so a match is never hidden by the selected tab
        for (Module m : order) {
            if (m.getName().toLowerCase(Locale.ROOT).contains(q) || m.getDescription().toLowerCase(Locale.ROOT).contains(q)) out.add(m);
        }
        return out;
    }

    /** The card Enter would toggle: the top match while searching. */
    private Module enterTarget(List<Module> modules) {
        return settingsFor == null && !query().isEmpty() && !modules.isEmpty() ? modules.get(0) : null;
    }

    /** "Left Alt" becomes "LAlt" so the key fits on a card. */
    private static String shortKey(String key) {
        if (key.startsWith("Left ")) return "L" + key.substring(5);
        if (key.startsWith("Right ")) return "R" + key.substring(6);
        return key;
    }

    // ── Geometry ────────────────────────────────────────────────────────

    private Rect content() {
        int top = py + HEADER + 4;
        return new Rect(px + SIDE + PAD, top, pw - SIDE - PAD * 2, py + ph - FOOTER - top - 2);
    }

    private Rect searchBox() {
        int w = Math.min(150, (pw - SIDE) / 2);
        return new Rect(px + pw - PAD - w, py + 9, w, 18);
    }

    /** Row pitch in the sidebar: rows squeeze together on very short windows instead of hitting Edit HUD. */
    private int rowStep(int count) {
        int room = editHudButton().y - 6 - (py + 40);
        return Math.max(13, Math.min(CAT_H + 2, room / Math.max(1, count)));
    }

    private Rect sidebarRow(int index, int count) {
        int step = rowStep(count);
        return new Rect(px + 8, py + 40 + index * step, SIDE - 16, step - 2);
    }

    private Rect editHudButton() {
        return new Rect(px + 8, py + ph - 28, SIDE - 16, 20);
    }

    private Rect backButton() {
        return new Rect(px + SIDE + PAD, py + 9, 18, 18);
    }

    private Rect resetButton() {
        int w = Icons.SIZE + 5 + textRenderer.getWidth("Reset") + 14;
        return new Rect(px + pw - PAD - w, py + 9, w, 18);
    }

    private int columns(int gridW) {
        return Math.max(1, (gridW + GAP) / (MIN_CARD_W + GAP));
    }

    private List<Rect> cardRects(List<Module> modules) {
        Rect area = content();
        int cols = columns(area.w);
        int cardW = (area.w - GAP * (cols - 1)) / cols;
        int offset = Math.round(scroll.get());
        List<Rect> rects = new ArrayList<>();
        for (int i = 0; i < modules.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            rects.add(new Rect(area.x + col * (cardW + GAP), area.y + row * (CARD_H + GAP) - offset, cardW, CARD_H));
        }
        return rects;
    }

    private float maxScroll(int count) {
        Rect area = content();
        int rows = (count + columns(area.w) - 1) / columns(area.w);
        return Math.max(0, rows * (CARD_H + GAP) - GAP - area.h);
    }

    private static Rect gearRect(Rect card) {
        return new Rect(card.x + card.w - 18, card.y + card.h - 17, 13, 13);
    }

    /** The favourite star sits left of the gear, or in its place on cards without settings. */
    private static Rect starRect(Rect card, boolean hasSettings) {
        return new Rect(card.x + card.w - 18 - (hasSettings ? 14 : 0), card.y + card.h - 17, 13, 13);
    }

    // ── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float t = open.update();
        layout(Math.round((1f - Animation.easeOutCubic(t)) * 16f));
        hoverHint = null;

        // opened from the main menu there is no world behind the panel, so draw the menu background instead of black
        if (client.world == null) Backgrounds.draw(context, ThemeSettings.get().menuBackground, width, height);
        context.fill(0, 0, width, height, Theme.withAlpha(0xB005060F, t));
        if (sharp()) {
            RenderUtil.panel(context, px, py, pw, ph, RADIUS, Theme.withAlpha(Theme.PANEL_DEEP, t), Theme.withAlpha(BORDER, t));
            RenderUtil.roundedRect(context, px + SIDE, py, 1, ph, 0, Theme.withAlpha(BORDER, t));
            RenderUtil.roundedRect(context, px + SIDE, py + HEADER, pw - SIDE, 1, 0, Theme.withAlpha(BORDER, t));
        } else {
            RenderUtil.glow(context, px, py, pw, ph, RADIUS, Theme.withAlpha(Theme.ACCENT, 0.42f * t), 14);
            RenderUtil.surface(context, px, py, pw, ph, RADIUS, Theme.withAlpha(Theme.PANEL_DEEP, t), Theme.withAlpha(Theme.BORDER_STRONG, t));
            // sidebar: a violet wash that is strongest behind the logo
            int wash = Theme.withAlpha(Theme.ACCENT, 0.10f * t);
            context.fillGradient(px + 1, py + RADIUS, px + SIDE, py + 34, wash & 0x00FFFFFF, wash);
            context.fillGradient(px + 1, py + 34, px + SIDE, py + ph - RADIUS, wash, wash & 0x00FFFFFF);
            RenderUtil.fadeLineVertical(context, px + SIDE, py + 8, py + ph - 8, Theme.withAlpha(Theme.BORDER_STRONG, t));
            // the Nebula signature line under the header
            RenderUtil.shimmerLine(context, px + SIDE + PAD, py + HEADER - 3, px + pw - PAD, 1, t);
        }

        renderSidebar(context, mouseX, mouseY, t);

        float p = page.update();
        Rect area = content();
        if (settingsFor == null) {
            renderGridHeader(context, mouseX, mouseY, delta, t);
            renderGrid(context, area, mouseX, mouseY, t, p);
        } else {
            // settings pages slide in from the right
            int slide = Math.round((1f - Animation.easeOutCubic(p)) * 12f);
            renderSettings(context, new Rect(area.x + slide, area.y, area.w, area.h), mouseX, mouseY, t * p);
        }
        renderFooter(context, t);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // the dim layer is drawn in render()
    }

    private void renderSidebar(DrawContext context, int mouseX, int mouseY, float t) {
        if (sharp()) {
            RenderUtil.roundedRect(context, px + 12, py + 15, 6, 6, 0, Theme.withAlpha(Theme.ACCENT, t));
            PixelFont.draw(context, "ASTRO", px + 23, py + 14, 2, 2, Theme.withAlpha(Theme.TEXT, t), Theme.withAlpha(Theme.TEXT, t));
        } else {
            int logoW = PixelFont.width("ASTRO", 2, 2);
            RenderUtil.glow(context, px + 14, py + 14, logoW, 14, 4, Theme.withAlpha(Theme.ACCENT, 0.28f * t), 8);
            // a band of light crosses the wordmark every few seconds
            float sheen = (Animation.clock() * 0.45f) % 5f - 0.3f;
            PixelFont.draw(context, "ASTRO", px + 14, py + 14, 2, 2, Theme.withAlpha(0xFFFFFFFF, t), Theme.withAlpha(Theme.ACCENT, t), sheen);
            // a small moon in orbit next to it
            float orbit = Animation.clock() * 0.9f;
            float ox = px + 14 + logoW + 11, oy = py + 21;
            RenderUtil.circle(context, ox, oy, 3f, Theme.withAlpha(Theme.lerp(DEEP, Theme.ACCENT, 0.75f), t));
            RenderUtil.circle(context, ox - 0.8f, oy - 0.8f, 1.4f, Theme.withAlpha(Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.35f), t));
            RenderUtil.circle(context, ox + MathHelper.cos(orbit) * 6.5f, oy + MathHelper.sin(orbit) * 2.6f, 1f, Theme.withAlpha(Theme.ACCENT_2, t));
        }
        boolean text = Theme.visible(t);

        List<Tab> tabs = tabs();
        int step = rowStep(tabs.size());
        int rowH = step - 2;
        // one highlight that slides between rows instead of each row fading on its own
        int selIndex = settingsFor == null && query().isEmpty() ? tabs.indexOf(tab) : -1;
        Animation selAlpha = anim("selector", "alpha", 0f, 14f);
        if (selIndex >= 0) {
            if (selAlpha.get() < 0.05f) selector.snap(selIndex);
            selector.setTarget(selIndex);
        }
        selAlpha.setTarget(selIndex >= 0 ? 1f : 0f);
        float selY = py + 40 + selector.update() * step;
        float sa = selAlpha.update();
        if (sa > 0.01f) {
            if (sharp()) {
                RenderUtil.roundedRect(context, px + 1, selY, SIDE - 1, rowH, 0, Theme.withAlpha(CARD_HOVER, t * sa));
                RenderUtil.roundedRect(context, px + 1, selY, 2, rowH, 0, Theme.withAlpha(Theme.ACCENT, t * sa));
            } else {
                RenderUtil.surface(context, px + 8, selY, SIDE - 16, rowH, 5, Theme.withAlpha(Theme.lerp(DEEP, Theme.ACCENT, 0.26f), t * sa),
                        Theme.withAlpha(Theme.lerp(DEEP, Theme.ACCENT, 0.62f), t * sa));
                // glowing violet-to-cyan marker
                RenderUtil.glow(context, px + 8, selY + 4, 2, rowH - 8, 1, Theme.withAlpha(Theme.ACCENT_2, 0.7f * t * sa), 4);
                RenderUtil.gradientRect(context, px + 8, selY + 4, 2, rowH - 8, 1, Theme.withAlpha(Theme.ACCENT_2, t * sa), Theme.withAlpha(Theme.ACCENT, t * sa));
            }
        }
        for (int i = 0; i < tabs.size(); i++) {
            Tab c = tabs.get(i);
            Rect row = sidebarRow(i, tabs.size());
            String label = c.label();
            boolean selected = i == selIndex;
            Animation hover = anim(label, "tab", 0f, HOVER_SPEED);
            hover.setTarget(row.contains(mouseX, mouseY) ? 1f : 0f);
            float h = hover.update();
            Animation sel = anim(label, "sel", selected ? 1f : 0f, 14f);
            sel.setTarget(selected ? 1f : 0f);
            float s = sel.update();

            if (h > 0.01f && !selected) RenderUtil.roundedRect(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(0x12FFFFFF, t * h));
            int textY = row.y + (row.h - 8) / 2;
            int nudge = sharp() ? 0 : Math.round(h * (1f - s));
            Icons.draw(context, c.icon(), row.x + 7 + nudge, row.y + (row.h - Icons.SIZE) / 2,
                    Theme.withAlpha(Theme.lerp(Theme.MUTED, c.favourites ? Theme.WARN : accent2(), Math.max(s, h * 0.5f)), t));
            if (!text) continue;
            context.drawText(textRenderer, label, row.x + 21 + nudge, textY,
                    Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.TEXT, Math.max(s, h * 0.7f)), t), false);
            List<Module> mods = inTab(c);
            int on = 0;
            for (Module m : mods) if (m.isEnabled()) on++;
            String count = c.favourites ? String.valueOf(mods.size()) : on + "/" + mods.size();
            int countX = row.x + row.w - 5 - textRenderer.getWidth(count);
            if (countX < row.x + 22 + textRenderer.getWidth(label) + 4) continue; // no room next to a long label
            boolean lit = c.favourites ? !mods.isEmpty() : on > 0;
            context.drawText(textRenderer, count, countX, textY,
                    Theme.withAlpha(lit ? Theme.lerp(Theme.MUTED, Theme.ACCENT_2, 0.6f) : Theme.MUTED, t), false);
        }

        Rect edit = editHudButton();
        Animation hover = anim("edit", "hover", 0f, HOVER_SPEED);
        hover.setTarget(edit.contains(mouseX, mouseY) ? 1f : 0f);
        float h = hover.update();
        if (edit.contains(mouseX, mouseY)) hoverHint = "Drag HUD elements where you want them";
        if (sharp()) {
            RenderUtil.panel(context, edit.x, edit.y, edit.w, edit.h, 5, Theme.withAlpha(Theme.lerp(CARD_HOVER, Theme.ACCENT, h), t),
                    Theme.withAlpha(Theme.lerp(BORDER, Theme.ACCENT, h), t));
        } else {
            if (h > 0.01f) RenderUtil.glow(context, edit.x, edit.y, edit.w, edit.h, 5, Theme.withAlpha(Theme.ACCENT, 0.5f * h * t), 5);
            RenderUtil.surface(context, edit.x, edit.y, edit.w, edit.h, 5, Theme.withAlpha(Theme.lerp(CARD_HOVER, Theme.ACCENT, h), t),
                    Theme.withAlpha(Theme.lerp(Theme.lerp(DEEP, Theme.ACCENT, 0.55f), Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.3f), h), t));
        }
        int onEdit = Theme.lerp(Theme.TEXT, Theme.ON_ACCENT, h);
        int labelW = Icons.SIZE + 5 + textRenderer.getWidth("Edit HUD");
        int lx = edit.x + (edit.w - labelW) / 2;
        Icons.draw(context, Icons.PENCIL, lx, edit.y + 6, Theme.withAlpha(onEdit, t));
        if (text) context.drawText(textRenderer, "Edit HUD", lx + Icons.SIZE + 5, edit.y + 6, Theme.withAlpha(onEdit, t), false);
    }

    private void renderGridHeader(DrawContext context, int mouseX, int mouseY, float delta, float t) {
        int x = px + SIDE + PAD;
        Rect box = searchBox();
        if (Theme.visible(t)) {
            String title = !query().isEmpty() ? "Search results" : tab.title();
            if (sharp()) {
                context.drawText(textRenderer, !query().isEmpty() ? "SEARCH" : "CATEGORY", x, py + 7, Theme.withAlpha(Theme.MUTED, t), false);
                context.drawText(textRenderer, title, x, py + 19, Theme.withAlpha(Theme.TEXT, t), false);
            } else {
                context.drawText(textRenderer, title, x, py + 14, Theme.withAlpha(0xFFFFFFFF, t), false);
                // how many of the listed mods are switched on
                List<Module> listed = visibleModules();
                int on = 0;
                for (Module m : listed) if (m.isEnabled()) on++;
                String count = on + " on";
                int cx = x + textRenderer.getWidth(title) + 6;
                int cw = textRenderer.getWidth(count) + 8;
                if (!listed.isEmpty() && cx + cw + 6 < box.x) {
                    RenderUtil.roundedRect(context, cx, py + 12, cw, 12, 6, Theme.withAlpha(Theme.lerp(DEEP, Theme.ACCENT, on > 0 ? 0.32f : 0.12f), t));
                    context.drawText(textRenderer, count, cx + 4, py + 14, Theme.withAlpha(on > 0 ? Theme.TEXT : Theme.MUTED, t), false);
                }
            }
        }
        Animation focus = anim("search", "focus", 0f, HOVER_SPEED);
        focus.setTarget(search.isFocused() ? 1f : 0f);
        float f = focus.update();
        if (sharp()) {
            RenderUtil.panel(context, box.x, box.y, box.w, box.h, 5, Theme.withAlpha(CARD_BG, t), Theme.withAlpha(Theme.lerp(BORDER, Theme.ACCENT, f), t));
        } else {
            if (f > 0.01f) RenderUtil.glow(context, box.x, box.y, box.w, box.h, 5, Theme.withAlpha(Theme.ACCENT, 0.5f * t * f), 5);
            RenderUtil.surface(context, box.x, box.y, box.w, box.h, 5, Theme.withAlpha(Theme.lerp(0xFF16143A, 0xFF1B1846, f), t),
                    Theme.withAlpha(Theme.lerp(RING_IDLE, Theme.ACCENT, f), t), 0.5f + 0.5f * f);
        }
        Icons.draw(context, Icons.SEARCH, box.x + 5, box.y + 5, Theme.withAlpha(Theme.lerp(Theme.MUTED, sharp() ? Theme.ACCENT : Theme.ACCENT_2, f), t));
        if (Theme.visible(t)) search.render(context, mouseX, mouseY, delta);
    }

    private void renderGrid(DrawContext context, Rect area, int mouseX, int mouseY, float t, float p) {
        List<Module> modules = visibleModules();
        float max = maxScroll(modules.size());
        scroll.setTarget(MathHelper.clamp(scroll.getTarget(), 0f, max));
        scroll.update();
        if (modules.isEmpty()) {
            boolean noFavourites = query().isEmpty() && tab.favourites;
            Icons.drawCentered(context, noFavourites ? Icons.STAR : Icons.SEARCH, area.x, area.y + 20, area.w, Icons.SIZE,
                    Theme.withAlpha(noFavourites ? Theme.WARN : Theme.MUTED, t));
            if (Theme.visible(t)) {
                context.drawCenteredTextWithShadow(textRenderer, noFavourites ? "No favourites yet" : "No mods match \"" + search.getText().trim() + "\"",
                        area.x + area.w / 2, area.y + 36, Theme.withAlpha(Theme.TEXT, t));
                context.drawCenteredTextWithShadow(textRenderer, noFavourites ? "Click the star on a mod to keep it here" : "Esc clears the search",
                        area.x + area.w / 2, area.y + 50, Theme.withAlpha(Theme.MUTED, t));
            }
            return;
        }
        Module target = enterTarget(modules);
        List<Rect> rects = cardRects(modules);
        context.enableScissor(area.x - 2, area.y - 3, area.x + area.w + 2, area.y + area.h);
        for (int i = 0; i < modules.size(); i++) {
            Rect r = rects.get(i);
            if (r.y + r.h < area.y - 3 || r.y > area.y + area.h) continue;
            boolean hovered = area.contains(mouseX, mouseY) && r.contains(mouseX, mouseY);
            // entrance: cards fade and rise in one after another
            float local = MathHelper.clamp(p * 1.6f - Math.min(i, 11) * 0.07f, 0f, 1f);
            int rise = Math.round((1f - Animation.easeOutCubic(local)) * 8f);
            renderCard(context, modules.get(i), new Rect(r.x, r.y + rise, r.w, r.h), i, hovered, modules.get(i) == target, mouseX, mouseY, t * local);
        }
        context.disableScissor();

        // soften the clipped edges while there is more to scroll to
        int edge = Theme.PANEL_DEEP & 0x00FFFFFF;
        int solid = Theme.withAlpha(0xFF000000 | edge, t);
        if (scroll.get() > 1f) context.fillGradient(area.x - 2, area.y - 3, area.x + area.w + 2, area.y + 9, solid, edge);
        if (scroll.get() < max - 1f) context.fillGradient(area.x - 2, area.y + area.h - 10, area.x + area.w + 2, area.y + area.h, edge, solid);

        if (max > 0) scrollbar(context, "grid", area.x + area.w + 4, area.y, area.h, scroll, max, t);
    }

    /** Slim scroll indicator: the thumb is an accent gradient that brightens while the list is moving. */
    private void scrollbar(DrawContext context, String key, int x, int y, int h, Animation position, float max, float t) {
        int thumbH = Math.max(16, Math.round(h * h / (h + max)));
        float thumbY = y + (h - thumbH) * MathHelper.clamp(position.get() / max, 0f, 1f);
        Animation moving = anim(key, "scrolling", 0f, 8f);
        moving.setTarget(Math.abs(position.getTarget() - position.get()) > 0.5f ? 1f : 0f);
        float m = moving.update();
        RenderUtil.roundedRect(context, x, y, 2, h, 1, Theme.withAlpha(BORDER, t));
        if (sharp()) {
            RenderUtil.roundedRect(context, x, thumbY, 2, thumbH, 1, Theme.withAlpha(Theme.ACCENT, t));
            return;
        }
        if (m > 0.02f) RenderUtil.glow(context, x, thumbY, 2, thumbH, 1, Theme.withAlpha(Theme.ACCENT_2, 0.6f * m * t), 4);
        float a = (0.6f + 0.4f * m) * t;
        RenderUtil.gradientRect(context, x, thumbY, 2, thumbH, 1, Theme.withAlpha(Theme.ACCENT_2, a), Theme.withAlpha(Theme.ACCENT, a));
    }

    private void renderCard(DrawContext context, Module m, Rect r, int index, boolean hovered, boolean target, int mouseX, int mouseY, float t) {
        Animation hover = anim(m, "hover", 0f, HOVER_SPEED);
        hover.setTarget(hovered ? 1f : 0f);
        float h = hover.update();
        Animation on = anim(m, "on", m.isEnabled() ? 1f : 0f, TOGGLE_SPEED);
        on.setTarget(m.isEnabled() ? 1f : 0f);
        float e = on.update();
        Animation press = anim(m, "press", 0f, 26f);
        press.setTarget(pressed == m && hovered ? 1f : 0f);
        float pr = press.update();

        boolean hasSettings = !m.getSettings().isEmpty();
        Rect gear = gearRect(r);
        Rect star = starRect(r, hasSettings);
        boolean overGear = hovered && hasSettings && gear.contains(mouseX, mouseY);
        boolean overStar = hovered && star.contains(mouseX, mouseY);
        if (hovered) {
            hoverHint = overStar ? (m.isPinned() ? "Remove from favourites" : "Add to favourites")
                    : overGear ? "Open " + m.getName() + " settings"
                    : hasSettings ? "Click: toggle · Right-click: settings" : "Click: toggle";
        }

        // glow pulse when the mod was just switched on or off (click, Enter or its keybind)
        Boolean was = lastEnabled.put(m, m.isEnabled());
        Animation pulse = anim(m, "pulse", 0f, PULSE_SPEED);
        if (was != null && was != m.isEnabled()) {
            pulse.snap(1f);
            pulse.setTarget(0f);
        }
        float pu = pulse.update();
        int base = Theme.lerp(Theme.lerp(CARD_BG, CARD_HOVER, h), Theme.ACCENT, sharp() ? 0f : 0.07f * e);

        if (sharp()) {
            if (pu > 0.02f) {
                RenderUtil.glow(context, r.x, r.y, r.w, r.h, 8, Theme.withAlpha(m.isEnabled() ? Theme.ACCENT_2 : Theme.MUTED, 0.6f * pu * t),
                        Math.round(3 + 5 * (1f - pu)));
            }
            int border = Theme.lerp(Theme.lerp(BORDER, Theme.ACCENT, e), Theme.TEXT, target ? 1f : h * 0.45f);
            RenderUtil.panel(context, r.x, r.y, r.w, r.h, 7, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, h), t), Theme.withAlpha(border, t));
        } else {
            // hovering lifts the card a pixel, pressing pushes it back down
            r = new Rect(r.x, r.y - (h > 0.5f && pr < 0.5f ? 1 : 0), r.w, r.h);
            gear = gearRect(r);
            star = starRect(r, hasSettings);
            float halo = Math.max(pu, h * 0.55f);
            if (halo > 0.02f) {
                int haloColor = pu > h * 0.55f ? (m.isEnabled() ? Theme.ACCENT_2 : Theme.MUTED) : Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, e * 0.5f);
                RenderUtil.glow(context, r.x, r.y, r.w, r.h, 8, Theme.withAlpha(haloColor, 0.6f * halo * t), Math.round(4 + 4 * (1f - pu)));
            }
            // ring: a hairline when off, violet melting into cyan when on (the cyan end breathes), cyan for the Enter target
            float breathe = 0.5f + 0.5f * MathHelper.sin(Animation.clock() * 1.1f + index * 0.9f);
            int ringTop = Theme.lerp(RING_IDLE, Theme.ACCENT, e);
            int ringBottom = Theme.lerp(RING_IDLE, Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, 0.45f + 0.55f * breathe), e);
            float toCyan = target ? 1f : h * 0.4f;
            ringTop = Theme.lerp(ringTop, Theme.ACCENT_2, toCyan);
            ringBottom = Theme.lerp(ringBottom, Theme.ACCENT_2, toCyan);
            RenderUtil.gradientRect(context, r.x - 1, r.y - 1, r.w + 2, r.h + 2, 8, Theme.withAlpha(ringTop, t), Theme.withAlpha(ringBottom, t));
            // body: lit from above, a touch of accent while enabled
            RenderUtil.gradientRect(context, r.x, r.y, r.w, r.h, 7, Theme.withAlpha(Theme.lerp(base, 0xFFFFFFFF, 0.045f), t),
                    Theme.withAlpha(Theme.lerp(base, DEEP, 0.3f), t));
            RenderUtil.roundedRect(context, r.x + 7, r.y, r.w - 14, 0.5f, 0, Theme.withAlpha(0x20FFFFFF, t));
        }

        // icon tile
        int tile = 20;
        int tx = r.x + 7;
        int ty = r.y + 7;
        if (sharp()) {
            RenderUtil.roundedRect(context, tx, ty, tile, tile, 5, Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.ACCENT, e), t));
        } else {
            if (e > 0.02f) RenderUtil.glow(context, tx, ty, tile, tile, 5, Theme.withAlpha(Theme.ACCENT, 0.5f * e * t), 4);
            RenderUtil.gradientRect(context, tx, ty, tile, tile, 5, Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.12f), e), t),
                    Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, 0.55f), e), t));
            RenderUtil.roundedRect(context, tx + 3, ty, tile - 6, 0.5f, 0, Theme.withAlpha(0x30FFFFFF, t));
        }
        String[] icon = Icons.forModule(m);
        int onTile = sharp() ? Theme.lerp(Theme.MUTED, Theme.ON_ACCENT, e) : Theme.lerp(0xFFB8B5E0, 0xFFFFFFFF, e);
        if (icon != null) Icons.drawCentered(context, icon, tx, ty, tile, tile, Theme.withAlpha(onTile, t));

        drawSwitch(context, r.x + r.w - 27, r.y + 12, e, t);

        boolean text = Theme.visible(t);
        // room is always kept for the star and gear, so the text doesn't shift when they appear
        String desc = trimmed(m.getDescription(), r.w - 14 - 15 - (hasSettings ? 15 : 0));
        if (text) {
            if (icon == null) {
                String initial = m.getName().substring(0, 1).toUpperCase(Locale.ROOT);
                context.drawText(textRenderer, initial, tx + (tile - textRenderer.getWidth(initial)) / 2 + 1, ty + 6, Theme.withAlpha(onTile, t), false);
            }
            String name = trimmed(m.getName(), r.w - 33 - 30);
            context.drawText(textRenderer, name, tx + tile + 6, r.y + 8, Theme.withAlpha(Theme.TEXT, t), false);
            String state = sharp() ? (m.isEnabled() ? "ON" : "OFF") : (m.isEnabled() ? "On" : "Off");
            int sx = tx + tile + 6;
            context.drawText(textRenderer, state, sx, r.y + 18, Theme.withAlpha(Theme.lerp(Theme.MUTED, accent2(), e), t), false);
            // the key this mod is used with, straight from Controls
            String key = m.keyHint();
            if (key != null) {
                key = shortKey(key);
                int kx = sx + textRenderer.getWidth(state) + 5;
                int kw = textRenderer.getWidth(key) + 6;
                if (kx + kw <= r.x + r.w - 30) {
                    RenderUtil.surface(context, kx, r.y + 17, kw, 10, 3, Theme.withAlpha(Theme.lerp(DEEP, CARD_BG, 0.4f), t), Theme.withAlpha(Theme.lerp(RING_IDLE, Theme.MUTED, 0.5f), t));
                    context.drawText(textRenderer, key, kx + 3, r.y + 18, Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.TEXT, 0.35f + 0.65f * e), t), false);
                }
            }
            context.drawText(textRenderer, desc, r.x + 7, r.y + r.h - 14, Theme.withAlpha(Theme.MUTED, t), false);
        }

        // favourite star: always there once pinned, otherwise it appears while the card is hovered
        Animation starShow = anim(m, "star", m.isPinned() ? 1f : 0f, HOVER_SPEED);
        starShow.setTarget(m.isPinned() ? 1f : hovered ? (overStar ? 1f : 0.6f) : 0f);
        float sv = starShow.update();
        if (sv > 0.02f) {
            if (overStar) RenderUtil.roundedRect(context, star.x, star.y, star.w, star.h, 3, Theme.withAlpha(0x22FFFFFF, t));
            int starColor = m.isPinned() ? Theme.WARN : Theme.lerp(Theme.MUTED, Theme.WARN, overStar ? 0.6f : 0f);
            Icons.drawCentered(context, Icons.STAR, star.x, star.y, star.w, star.h, Theme.withAlpha(starColor, t * sv));
        }

        if (hasSettings) {
            Animation gearHover = anim(m, "gear", 0f, HOVER_SPEED);
            gearHover.setTarget(overGear ? 1f : 0f);
            float gh = gearHover.update();
            if (gh > 0.01f) RenderUtil.roundedRect(context, gear.x, gear.y, gear.w, gear.h, 3, Theme.withAlpha(0x22FFFFFF, t * gh));
            Icons.drawCentered(context, Icons.GEAR, gear.x, gear.y, gear.w, gear.h,
                    Theme.withAlpha(Theme.lerp(Theme.lerp(Theme.BORDER_STRONG, Theme.MUTED, h), accent2(), gh), t));
        }
    }

    private String trimmed(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, maxWidth - textRenderer.getWidth("…")) + "…";
    }

    private void drawSwitch(DrawContext context, int x, int y, float k, float t) {
        int sw = 20;
        int sh = 10;
        if (sharp()) {
            // Orbit: square track with a hairline border and a square knob
            RenderUtil.panel(context, x, y, sw, sh, 0, Theme.withAlpha(Theme.lerp(Theme.PANEL_DEEP, Theme.withAlpha(Theme.ACCENT, 0.14f), k), t),
                    Theme.withAlpha(Theme.lerp(BORDER, Theme.ACCENT, k), t));
            RenderUtil.roundedRect(context, x + 2 + (sw - sh) * k, y + 2, sh - 4, sh - 4, 0, Theme.withAlpha(Theme.lerp(Theme.BORDER_STRONG, Theme.ACCENT, k), t));
            return;
        }
        // track: violet running into cyan while on
        int left = Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.ACCENT, k), t);
        int right = Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, 0.75f), k), t);
        if (k > 0.02f) RenderUtil.glow(context, x, y, sw, sh, sh / 2f, Theme.withAlpha(Theme.ACCENT, 0.45f * k * t), 3);
        RenderUtil.roundedRect(context, x, y, sw, sh, sh / 2f, left);
        if (k > 0.02f) {
            RenderUtil.roundedRect(context, x + sw - sh, y, sh, sh, sh / 2f, right);
            RenderUtil.horizontalGradient(context, x + sh / 2, y, x + sw - sh / 2, y + sh, left, right);
        }
        RenderUtil.circle(context, x + sh / 2f + (sw - sh) * k, y + sh / 2f, sh / 2f - 1.5f,
                Theme.withAlpha(Theme.lerp(0xFFA9A6D4, 0xFFFFFFFF, k), t));
    }

    private void renderFooter(DrawContext context, float t) {
        if (!Theme.visible(t)) return;
        int x = px + SIDE + PAD;
        int y = py + ph - FOOTER + 4;
        int right = px + pw - PAD;
        String close = settingsFor == null ? "Esc to close" : "Esc to go back";
        int closeW = textRenderer.getWidth(close);
        context.drawText(textRenderer, close, right - closeW, y, Theme.withAlpha(Theme.MUTED, t), false);

        String hint;
        Module target = settingsFor == null ? enterTarget(visibleModules()) : null;
        if (hoverHint != null) hint = hoverHint;
        else if (target != null) hint = "Enter to " + (target.isEnabled() ? "disable " : "enable ") + target.getName();
        else if (settingsFor == null) hint = "Type to search · Tab switches category";
        else hint = "Right-click: previous option";
        hint = trimmed(hint, right - closeW - 12 - x);
        context.drawText(textRenderer, hint, x, y, Theme.withAlpha(target != null && hoverHint == null ? Theme.ACCENT : Theme.MUTED, t), false);
    }

    private int settingRowHeight(Setting<?> s) {
        return s instanceof NumberSetting ? 30 : 22; // switches and mode pickers are one line
    }

    private List<Rect> settingRows(Rect area) {
        List<Rect> rows = new ArrayList<>();
        int y = area.y + SETTINGS_TOP - Math.round(settingsScroll.get());
        rows.add(new Rect(area.x, y, area.w, 22)); // the "Enabled" row
        y += 22 + 4;
        for (Setting<?> s : settingsFor.getSettings()) {
            int h = settingRowHeight(s);
            rows.add(new Rect(area.x, y, area.w, h));
            y += h + 4;
        }
        return rows;
    }

    private void renderSettings(DrawContext context, Rect area, int mouseX, int mouseY, float t) {
        boolean text = Theme.visible(t);
        Rect back = backButton();
        Animation backHover = anim("back", "hover", 0f, HOVER_SPEED);
        backHover.setTarget(back.contains(mouseX, mouseY) ? 1f : 0f);
        float bh = backHover.update();
        if (back.contains(mouseX, mouseY)) hoverHint = "Back to the mod list";
        RenderUtil.surface(context, back.x, back.y, back.w, back.h, 5, Theme.withAlpha(Theme.lerp(CARD_HOVER, Theme.ACCENT, bh), t),
                Theme.withAlpha(Theme.lerp(sharp() ? BORDER : RING_IDLE, Theme.ACCENT, bh), t));
        Icons.drawCentered(context, Icons.BACK, back.x, back.y, back.w, back.h, Theme.withAlpha(Theme.lerp(Theme.TEXT, Theme.ON_ACCENT, bh), t));
        String[] icon = Icons.forModule(settingsFor);
        int titleX = back.x + back.w + 8;
        if (icon != null) {
            Icons.draw(context, icon, titleX, py + 13, Theme.withAlpha(accent2(), t));
            titleX += Icons.SIZE + 6;
        }

        // reset to defaults: only lights up once something differs from them
        Rect reset = resetButton();
        boolean canReset = !settingsFor.hasDefaultSettings();
        boolean overReset = reset.contains(mouseX, mouseY);
        Animation resetHover = anim("reset", "hover", 0f, HOVER_SPEED);
        resetHover.setTarget(overReset && canReset ? 1f : 0f);
        float rh = resetHover.update();
        Animation resetReady = anim("reset", "ready", canReset ? 1f : 0f, 10f);
        resetReady.setTarget(canReset ? 1f : 0f);
        float rr = resetReady.update();
        if (overReset) hoverHint = canReset ? "Put " + settingsFor.getName() + "'s settings back to defaults" : "Settings are already at their defaults";
        if (!sharp() && rh > 0.01f) RenderUtil.glow(context, reset.x, reset.y, reset.w, reset.h, 5, Theme.withAlpha(Theme.ACCENT, 0.5f * rh * t), 5);
        RenderUtil.surface(context, reset.x, reset.y, reset.w, reset.h, 5, Theme.withAlpha(Theme.lerp(Theme.lerp(CARD_BG, CARD_HOVER, rr), Theme.ACCENT, rh), t),
                Theme.withAlpha(Theme.lerp(Theme.lerp(sharp() ? BORDER : RING_IDLE, sharp() ? Theme.BORDER_STRONG : Theme.lerp(DEEP, Theme.ACCENT, 0.6f), rr), Theme.ACCENT, rh), t));
        int resetInk = Theme.lerp(Theme.lerp(Theme.lerp(Theme.MUTED, DEEP, 0.35f), Theme.TEXT, rr), Theme.ON_ACCENT, rh);
        Icons.draw(context, Icons.RESET, reset.x + 7, reset.y + 5, Theme.withAlpha(resetInk, t));
        if (text) context.drawText(textRenderer, "Reset", reset.x + 7 + Icons.SIZE + 5, reset.y + 5, Theme.withAlpha(resetInk, t), false);

        if (text) {
            context.drawText(textRenderer, settingsFor.getName(), titleX, py + 14, Theme.withAlpha(Theme.TEXT, t), false);
            // the key it is used with (set in Controls)
            String key = settingsFor.keyHint();
            int kx = titleX + textRenderer.getWidth(settingsFor.getName()) + 8;
            if (key != null && kx + textRenderer.getWidth(key) + 8 < reset.x - 6) {
                int kw = textRenderer.getWidth(key) + 8;
                RenderUtil.surface(context, kx, py + 12, kw, 12, 3, Theme.withAlpha(Theme.lerp(DEEP, CARD_BG, 0.6f), t), Theme.withAlpha(Theme.lerp(RING_IDLE, Theme.MUTED, 0.5f), t));
                context.drawText(textRenderer, key, kx + 4, py + 14, Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.TEXT, 0.6f), t), false);
            }
            context.drawText(textRenderer, trimmed(settingsFor.getDescription(), area.w), area.x, area.y + 2, Theme.withAlpha(Theme.MUTED, t), false);
        }

        float max = maxSettingsScroll(area);
        settingsScroll.setTarget(MathHelper.clamp(settingsScroll.getTarget(), 0f, max));
        settingsScroll.update();
        List<Rect> rows = settingRows(area);
        context.enableScissor(area.x - 2, area.y + SETTINGS_TOP - 2, area.x + area.w + 2, area.y + area.h);
        renderSwitchRow(context, rows.get(0), "Enabled", settingsFor.isEnabled(), settingsFor, mouseX, mouseY, t);
        List<Setting<?>> settings = settingsFor.getSettings();
        for (int i = 0; i < settings.size(); i++) {
            Rect row = rows.get(i + 1);
            Setting<?> s = settings.get(i);
            if (s instanceof BooleanSetting bool) renderSwitchRow(context, row, bool.getName(), bool.get(), bool, mouseX, mouseY, t);
            else if (s instanceof NumberSetting num) renderSliderRow(context, row, num, mouseX, mouseY, t);
            else if (s instanceof ModeSetting mode) renderModeRow(context, row, mode, mouseX, mouseY, t);
        }
        context.disableScissor();

        if (max > 0) scrollbar(context, "settings", area.x + area.w + 4, area.y + SETTINGS_TOP, area.h - SETTINGS_TOP, settingsScroll, max, t);
    }

    private float maxSettingsScroll(Rect area) {
        if (settingsFor == null) return 0;
        int total = 22 + 4;
        for (Setting<?> s : settingsFor.getSettings()) total += settingRowHeight(s) + 4;
        return Math.max(0, total - (area.h - SETTINGS_TOP));
    }

    private float rowHover(Object key, Rect row, int mouseX, int mouseY) {
        Animation hover = anim(key, "row", 0f, HOVER_SPEED);
        hover.setTarget(row.contains(mouseX, mouseY) ? 1f : 0f);
        return hover.update();
    }

    /** Background shared by the settings rows; they flash cyan for a moment after Reset. */
    private void settingRow(DrawContext context, Rect row, float hover, float t) {
        float flash = anim("reset", "flash", 0f, 4f).update();
        int border = sharp() ? Theme.lerp(BORDER, Theme.BORDER_STRONG, hover) : Theme.lerp(RING_IDLE, Theme.lerp(DEEP, Theme.ACCENT, 0.7f), hover);
        RenderUtil.surface(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, hover), t),
                Theme.withAlpha(Theme.lerp(border, accent2(), flash), t));
    }

    private void renderModeRow(DrawContext context, Rect row, ModeSetting mode, int mouseX, int mouseY, float t) {
        float hover = rowHover(mode, row, mouseX, mouseY);
        settingRow(context, row, hover, t);
        int valueW = textRenderer.getWidth(mode.get());
        int rightArrowX = row.x + row.w - 8 - 3;
        int valueX = rightArrowX - 6 - valueW;
        int leftArrowX = valueX - 6 - 3;
        int arrowY = row.y + (row.h - 5) / 2;
        int arrow = Theme.withAlpha(Theme.lerp(Theme.MUTED, accent2(), hover), t);
        Icons.draw(context, Icons.LEFT, leftArrowX, arrowY, arrow);
        Icons.draw(context, Icons.RIGHT, rightArrowX, arrowY, arrow);
        if (!Theme.visible(t)) return;
        context.drawText(textRenderer, mode.getName(), row.x + 8, row.y + (row.h - 8) / 2, Theme.withAlpha(Theme.TEXT, t), false);
        context.drawText(textRenderer, mode.get(), valueX, row.y + (row.h - 8) / 2, Theme.withAlpha(Theme.TEXT, t), false);
    }

    private void renderSwitchRow(DrawContext context, Rect row, String label, boolean value, Object key, int mouseX, int mouseY, float t) {
        float hover = rowHover(key, row, mouseX, mouseY);
        settingRow(context, row, hover, t);
        Animation knob = anim(key, "knob", value ? 1f : 0f, TOGGLE_SPEED);
        knob.setTarget(value ? 1f : 0f);
        drawSwitch(context, row.x + row.w - 28, row.y + (row.h - 10) / 2, knob.update(), t);
        if (Theme.visible(t)) context.drawText(textRenderer, label, row.x + 8, row.y + (row.h - 8) / 2, Theme.withAlpha(Theme.TEXT, t), false);
    }

    private void renderSliderRow(DrawContext context, Rect row, NumberSetting num, int mouseX, int mouseY, float t) {
        float hover = Math.max(rowHover(num, row, mouseX, mouseY), num == activeSlider ? 1f : 0f);
        settingRow(context, row, hover, t);
        if (Theme.visible(t)) {
            context.drawText(textRenderer, num.getName(), row.x + 8, row.y + 6, Theme.withAlpha(Theme.TEXT, t), false);
            String value = num.format();
            context.drawText(textRenderer, value, row.x + row.w - 8 - textRenderer.getWidth(value), row.y + 6, Theme.withAlpha(accent2(), t), false);
        }
        int trackX = row.x + 8;
        int trackW = row.w - 16;
        int trackY = row.y + 20;
        // the fill eases to its new place (after Reset, or when the value snaps to a step)
        Animation shown = anim(num, "fill", (float) num.getFraction(), 22f);
        shown.setTarget((float) num.getFraction());
        float frac = shown.update();
        RenderUtil.roundedRect(context, trackX, trackY, trackW, 4, 2, Theme.withAlpha(OFF_BAR, t));
        if (sharp()) {
            // Orbit: solid accent fill and a square handle
            RenderUtil.roundedRect(context, trackX, trackY, Math.round(trackW * frac), 4, 0, Theme.withAlpha(Theme.ACCENT, t));
            float k = 3f + hover;
            RenderUtil.roundedRect(context, trackX + trackW * frac - k, trackY + 2 - k, k * 2, k * 2, 0, Theme.withAlpha(Theme.TEXT, t));
            return;
        }
        RenderUtil.horizontalGradient(context, trackX + 1, trackY + 1, Math.round(trackX + trackW * frac), trackY + 3,
                Theme.withAlpha(Theme.ACCENT, t), Theme.withAlpha(Theme.ACCENT_2, t));
        float knobR = 3.5f + hover;
        float kx = trackX + trackW * frac;
        if (hover > 0.02f) RenderUtil.glow(context, kx - knobR, trackY + 2 - knobR, knobR * 2, knobR * 2, knobR, Theme.withAlpha(Theme.ACCENT_2, 0.6f * hover * t), 4);
        RenderUtil.circle(context, kx, trackY + 2, knobR, Theme.withAlpha(0xFFFFFFFF, t));
    }

    // ── Input ───────────────────────────────────────────────────────────

    private void selectTab(Tab c) {
        if (!c.equals(tab) || settingsFor != null) pageIn();
        tab = c;
        lastTab = c;
        settingsFor = null;
        reorder();
        scroll.snap(0f);
    }

    private void closeSettings() {
        settingsFor = null;
        pageIn();
    }

    private void openSettings(Module m) {
        settingsFor = m;
        pageIn();
        settingsScroll.snap(0f);
        search.setFocused(false);
        setFocused(null);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        if (button == 0 && editHudButton().contains(mouseX, mouseY)) {
            client.setScreen(new HudEditorScreen(this));
            return true;
        }
        if (settingsFor == null && searchBox().contains(mouseX, mouseY)) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        search.setFocused(false);
        setFocused(null);

        List<Tab> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (sidebarRow(i, tabs.size()).contains(mouseX, mouseY)) {
                selectTab(tabs.get(i));
                return true;
            }
        }

        Rect area = content();
        if (settingsFor == null) {
            if (!area.contains(mouseX, mouseY)) return false;
            List<Module> modules = visibleModules();
            List<Rect> rects = cardRects(modules);
            for (int i = 0; i < modules.size(); i++) {
                Rect r = rects.get(i);
                if (!r.contains(mouseX, mouseY)) continue;
                Module m = modules.get(i);
                boolean hasSettings = !m.getSettings().isEmpty();
                if (button == 2 || (button == 0 && starRect(r, hasSettings).contains(mouseX, mouseY))) m.setPinned(!m.isPinned());
                else if (hasSettings && (button == 1 || gearRect(r).contains(mouseX, mouseY))) openSettings(m);
                else if (button == 0) {
                    m.toggle();
                    pressed = m;
                }
                return true;
            }
            return true;
        }

        if (backButton().contains(mouseX, mouseY)) {
            closeSettings();
            return true;
        }
        if (button == 0 && resetButton().contains(mouseX, mouseY)) {
            if (!settingsFor.hasDefaultSettings()) {
                settingsFor.resetSettings();
                Animation flash = anim("reset", "flash", 0f, 4f);
                flash.snap(1f);
                flash.setTarget(0f);
            }
            return true;
        }
        if (!new Rect(area.x, area.y + SETTINGS_TOP, area.w, area.h - SETTINGS_TOP).contains(mouseX, mouseY)) return false;
        List<Rect> rows = settingRows(area);
        if (rows.get(0).contains(mouseX, mouseY)) {
            settingsFor.toggle();
            return true;
        }
        List<Setting<?>> settings = settingsFor.getSettings();
        for (int i = 0; i < settings.size(); i++) {
            Rect row = rows.get(i + 1);
            if (!row.contains(mouseX, mouseY)) continue;
            Setting<?> s = settings.get(i);
            if (s instanceof BooleanSetting bool) bool.toggle();
            else if (s instanceof ModeSetting mode) {
                if (button == 1) mode.cycleBack();
                else mode.cycle();
            } else if (s instanceof NumberSetting num) {
                activeSlider = num;
                sliderX = row.x + 8;
                sliderW = row.w - 16;
                num.setFraction((mouseX - sliderX) / sliderW);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button) {
        if (activeSlider == null) return false;
        activeSlider.setFraction((mouseX - sliderX) / sliderW);
        return true;
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        activeSlider = null;
        pressed = null;
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (settingsFor == null) {
            float max = maxScroll(visibleModules().size());
            scroll.setTarget(MathHelper.clamp(scroll.getTarget() - (float) verticalAmount * SCROLL_STEP, 0f, max));
        } else {
            settingsScroll.setTarget(MathHelper.clamp(settingsScroll.getTarget() - (float) verticalAmount * SCROLL_STEP, 0f, maxSettingsScroll(content())));
        }
        return true;
    }

    @Override
    protected boolean onKeyPressed(int key, int scancode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (settingsFor != null) closeSettings();
            else if (!search.getText().isEmpty()) search.setText("");
            else close();
            return true;
        }
        if (Keys.matches(AstroClient.get().clickGuiKey(), key, scancode, modifiers)) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            List<Tab> tabs = tabs();
            int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1;
            selectTab(tabs.get(Math.floorMod(tabs.indexOf(tab) + step, tabs.size())));
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            Module target = enterTarget(visibleModules());
            if (target != null) target.toggle();
            return target != null;
        }
        if (ctrl && key == GLFW.GLFW_KEY_F && settingsFor == null) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        return false;
    }

    @Override
    protected boolean onCharTyped(String chars) {
        // Typing anywhere goes to the search box, so you can just start typing a mod's name.
        if (settingsFor == null && !search.isFocused()) {
            setFocused(search);
            search.setFocused(true);
        }
        return false;
    }

    @Override
    public void tick() {
        if (closing && open.get() <= 0.02f) super.close();
    }

    @Override
    public void close() {
        if (closing) return;
        closing = true;
        open.setTarget(0f);
        AstroClient.get().saveConfig();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
