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
 * Clicking a card toggles it; the gear (or right-click) opens that module's settings page.
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
    private static final int CARD_BG = 0xFF17153A;
    private static final int CARD_HOVER = 0xFF221D4F;
    private static final int OFF_BAR = 0xFF2A2750;
    private static final int BORDER = 0x26FFFFFF;

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

    /** Tab survives closing and reopening the menu; null = All. */
    private static Category lastTab;

    private final Animation open = new Animation(0f, OPEN_SPEED);
    private final Animation scroll = new Animation(0f, SCROLL_SPEED);
    private final Animation settingsScroll = new Animation(0f, SCROLL_SPEED);
    private final Animation page = new Animation(0f, PAGE_SPEED);
    private final Animation selector = new Animation(0f, 14f);
    private final Map<Object, Animation> anims = new HashMap<>();
    private final Map<Module, Boolean> lastEnabled = new HashMap<>();
    private TextFieldWidget search;
    private Category tab = lastTab;
    private Module settingsFor;
    private boolean closing;
    private NumberSetting activeSlider;
    private int sliderX;
    private int sliderW;

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
    }

    /** Replays the content entrance: cards (or settings rows) fade and slide in. */
    private void pageIn() {
        page.snap(0f);
        page.setTarget(1f);
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

    private Animation anim(Object key, String kind, float initial, float speed) {
        return anims.computeIfAbsent(List.of(key, kind), k -> new Animation(initial, speed));
    }

    // ── Data ────────────────────────────────────────────────────────────

    private List<Category> tabs() {
        List<Category> list = new ArrayList<>();
        list.add(null);
        for (Category c : Category.values()) if (!AstroClient.get().modules().byCategory(c).isEmpty()) list.add(c);
        return list;
    }

    private static List<Module> inTab(Category c) {
        return c == null ? AstroClient.get().modules().all() : AstroClient.get().modules().byCategory(c);
    }

    private String query() {
        return search.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<Module> visibleModules() {
        String q = query();
        List<Module> out = new ArrayList<>();
        // searching looks through every category, so a match is never hidden by the selected tab
        for (Module m : q.isEmpty() ? inTab(tab) : AstroClient.get().modules().all()) {
            if (!q.isEmpty() && !m.getName().toLowerCase(Locale.ROOT).contains(q)
                    && !m.getDescription().toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(m);
        }
        return out;
    }

    /** The card Enter would toggle: the top match while searching. */
    private Module enterTarget(List<Module> modules) {
        return settingsFor == null && !query().isEmpty() && !modules.isEmpty() ? modules.get(0) : null;
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

    private Rect sidebarRow(int index) {
        return new Rect(px + 8, py + 40 + index * (CAT_H + 2), SIDE - 16, CAT_H);
    }

    private Rect editHudButton() {
        return new Rect(px + 8, py + ph - 28, SIDE - 16, 20);
    }

    private Rect backButton() {
        return new Rect(px + SIDE + PAD, py + 9, 18, 18);
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

    // ── Rendering ───────────────────────────────────────────────────────

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float t = open.update();
        layout(Math.round((1f - Animation.easeOutCubic(t)) * 16f));

        // opened from the main menu there is no world behind the panel, so draw the menu background instead of black
        if (client.world == null) Backgrounds.draw(context, ThemeSettings.get().menuBackground, width, height);
        context.fill(0, 0, width, height, Theme.withAlpha(0xB005060F, t));
        RenderUtil.glow(context, px, py, pw, ph, RADIUS, Theme.withAlpha(Theme.ACCENT, 0.4f * t), 12);
        RenderUtil.roundedRect(context, px, py, pw, ph, RADIUS, Theme.withAlpha(0xF20D0B22, t));
        context.fill(px + SIDE, py + 10, px + SIDE + 1, py + ph - 10, Theme.withAlpha(BORDER, t));

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
        PixelFont.draw(context, "ASTRO", px + 14, py + 14, 2, 2, Theme.withAlpha(0xFFFFFFFF, t), Theme.withAlpha(Theme.ACCENT, t));
        boolean text = Theme.visible(t);

        List<Category> tabs = tabs();
        // one highlight that slides between rows instead of each row fading on its own
        int selIndex = settingsFor == null && query().isEmpty() ? tabs.indexOf(tab) : -1;
        Animation selAlpha = anim("selector", "alpha", 0f, 14f);
        if (selIndex >= 0) {
            if (selAlpha.get() < 0.05f) selector.snap(selIndex);
            selector.setTarget(selIndex);
        }
        selAlpha.setTarget(selIndex >= 0 ? 1f : 0f);
        float selY = py + 40 + selector.update() * (CAT_H + 2);
        float sa = selAlpha.update();
        if (sa > 0.01f) {
            RenderUtil.roundedRect(context, px + 8, selY, SIDE - 16, CAT_H, 5, Theme.withAlpha(Theme.withAlpha(Theme.ACCENT, 0.22f), t * sa));
            RenderUtil.roundedRect(context, px + 8, selY + 5, 2, CAT_H - 10, 1, Theme.withAlpha(Theme.ACCENT_2, t * sa));
        }
        for (int i = 0; i < tabs.size(); i++) {
            Category c = tabs.get(i);
            Rect row = sidebarRow(i);
            String label = c == null ? "All" : c.displayName();
            boolean selected = i == selIndex;
            Animation hover = anim(label, "tab", 0f, HOVER_SPEED);
            hover.setTarget(row.contains(mouseX, mouseY) ? 1f : 0f);
            float h = hover.update();
            Animation sel = anim(label, "sel", selected ? 1f : 0f, 14f);
            sel.setTarget(selected ? 1f : 0f);
            float s = sel.update();

            if (h > 0.01f && !selected) RenderUtil.roundedRect(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(0x10FFFFFF, t * h));
            Icons.draw(context, Icons.forCategory(c), row.x + 7, row.y + (row.h - Icons.SIZE) / 2,
                    Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.ACCENT_2, s), t));
            if (!text) continue;
            context.drawText(textRenderer, label, row.x + 21, row.y + 6,
                    Theme.withAlpha(Theme.lerp(Theme.MUTED, 0xFFFFFFFF, Math.max(s, h * 0.7f)), t), false);
            List<Module> mods = inTab(c);
            long on = mods.stream().filter(Module::isEnabled).count();
            String count = on + "/" + mods.size();
            int countX = row.x + row.w - 5 - textRenderer.getWidth(count);
            if (countX < row.x + 21 + textRenderer.getWidth(label) + 4) continue; // no room next to a long label
            context.drawText(textRenderer, count, countX, row.y + 6,
                    Theme.withAlpha(on > 0 ? Theme.lerp(Theme.MUTED, Theme.ACCENT_2, 0.6f) : Theme.MUTED, t), false);
        }

        Rect edit = editHudButton();
        Animation hover = anim("edit", "hover", 0f, HOVER_SPEED);
        hover.setTarget(edit.contains(mouseX, mouseY) ? 1f : 0f);
        float h = hover.update();
        RenderUtil.roundedRect(context, edit.x, edit.y, edit.w, edit.h, 5, Theme.withAlpha(Theme.lerp(CARD_HOVER, Theme.ACCENT, h), t));
        int labelW = Icons.SIZE + 5 + textRenderer.getWidth("Edit HUD");
        int lx = edit.x + (edit.w - labelW) / 2;
        Icons.draw(context, Icons.PENCIL, lx, edit.y + 6, Theme.withAlpha(0xFFFFFFFF, t));
        if (text) context.drawText(textRenderer, "Edit HUD", lx + Icons.SIZE + 5, edit.y + 6, Theme.withAlpha(0xFFFFFFFF, t), false);
    }

    private void renderGridHeader(DrawContext context, int mouseX, int mouseY, float delta, float t) {
        int x = px + SIDE + PAD;
        if (Theme.visible(t)) {
            String title = !query().isEmpty() ? "Search results" : tab == null ? "All mods" : tab.displayName();
            context.drawText(textRenderer, title, x, py + 14, Theme.withAlpha(0xFFFFFFFF, t), false);
        }
        Rect box = searchBox();
        Animation focus = anim("search", "focus", 0f, HOVER_SPEED);
        focus.setTarget(search.isFocused() ? 1f : 0f);
        float f = focus.update();
        if (f > 0.01f) RenderUtil.roundedRect(context, box.x - 1, box.y - 1, box.w + 2, box.h + 2, 6, Theme.withAlpha(Theme.ACCENT, t * f));
        RenderUtil.roundedRect(context, box.x, box.y, box.w, box.h, 5, Theme.withAlpha(Theme.lerp(0xFF16143A, 0xFF1B1846, f), t));
        Icons.draw(context, Icons.SEARCH, box.x + 5, box.y + 5, Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.ACCENT_2, f), t));
        if (Theme.visible(t)) search.render(context, mouseX, mouseY, delta);
    }

    private void renderGrid(DrawContext context, Rect area, int mouseX, int mouseY, float t, float p) {
        List<Module> modules = visibleModules();
        float max = maxScroll(modules.size());
        scroll.setTarget(MathHelper.clamp(scroll.getTarget(), 0f, max));
        scroll.update();
        if (modules.isEmpty()) {
            Icons.drawCentered(context, Icons.SEARCH, area.x, area.y + 20, area.w, Icons.SIZE, Theme.withAlpha(Theme.MUTED, t));
            if (Theme.visible(t)) {
                context.drawCenteredTextWithShadow(textRenderer, "No mods match \"" + search.getText().trim() + "\"",
                        area.x + area.w / 2, area.y + 36, Theme.withAlpha(Theme.TEXT, t));
                context.drawCenteredTextWithShadow(textRenderer, "Esc clears the search", area.x + area.w / 2, area.y + 50,
                        Theme.withAlpha(Theme.MUTED, t));
            }
            return;
        }
        Module target = enterTarget(modules);
        List<Rect> rects = cardRects(modules);
        context.enableScissor(area.x - 2, area.y - 1, area.x + area.w + 2, area.y + area.h);
        for (int i = 0; i < modules.size(); i++) {
            Rect r = rects.get(i);
            if (r.y + r.h < area.y || r.y > area.y + area.h) continue;
            boolean hovered = area.contains(mouseX, mouseY) && r.contains(mouseX, mouseY);
            // entrance: cards fade and rise in one after another
            float local = MathHelper.clamp(p * 1.6f - Math.min(i, 11) * 0.07f, 0f, 1f);
            int rise = Math.round((1f - Animation.easeOutCubic(local)) * 8f);
            renderCard(context, modules.get(i), new Rect(r.x, r.y + rise, r.w, r.h), hovered, modules.get(i) == target, mouseX, mouseY, t * local);
        }
        context.disableScissor();

        // soften the clipped edges while there is more to scroll to
        int edge = 0x000D0B22;
        int solid = Theme.withAlpha(0xFF000000 | edge, t);
        if (scroll.get() > 1f) context.fillGradient(area.x - 2, area.y - 1, area.x + area.w + 2, area.y + 9, solid, edge);
        if (scroll.get() < max - 1f) context.fillGradient(area.x - 2, area.y + area.h - 10, area.x + area.w + 2, area.y + area.h, edge, solid);

        if (max > 0) {
            int thumbH = Math.max(16, Math.round(area.h * area.h / (area.h + max)));
            float thumbY = area.y + (area.h - thumbH) * (scroll.get() / max);
            RenderUtil.roundedRect(context, area.x + area.w + 4, area.y, 2, area.h, 1, Theme.withAlpha(0x14FFFFFF, t));
            RenderUtil.roundedRect(context, area.x + area.w + 4, thumbY, 2, thumbH, 1, Theme.withAlpha(Theme.ACCENT, t));
        }
    }

    private void renderCard(DrawContext context, Module m, Rect r, boolean hovered, boolean target, int mouseX, int mouseY, float t) {
        Animation hover = anim(m, "hover", 0f, HOVER_SPEED);
        hover.setTarget(hovered ? 1f : 0f);
        float h = hover.update();
        Animation on = anim(m, "on", m.isEnabled() ? 1f : 0f, TOGGLE_SPEED);
        on.setTarget(m.isEnabled() ? 1f : 0f);
        float e = on.update();

        // glow pulse when the mod was just switched on or off (click, Enter or its keybind)
        Boolean was = lastEnabled.put(m, m.isEnabled());
        Animation pulse = anim(m, "pulse", 0f, PULSE_SPEED);
        if (was != null && was != m.isEnabled()) {
            pulse.snap(1f);
            pulse.setTarget(0f);
        }
        float pu = pulse.update();
        if (pu > 0.02f) {
            RenderUtil.glow(context, r.x, r.y, r.w, r.h, 8, Theme.withAlpha(m.isEnabled() ? Theme.ACCENT_2 : Theme.MUTED, 0.6f * pu * t),
                    Math.round(3 + 5 * (1f - pu)));
        }

        // border: accent while enabled, brighter on hover, cyan for the Enter target
        int border = Theme.lerp(BORDER, Theme.withAlpha(Theme.ACCENT, 0.75f), e);
        border = Theme.lerp(border, Theme.ACCENT_2, target ? 1f : h * 0.45f);
        RenderUtil.roundedRect(context, r.x - 1, r.y - 1, r.w + 2, r.h + 2, 8, Theme.withAlpha(border, t));
        RenderUtil.roundedRect(context, r.x, r.y, r.w, r.h, 7, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, h), t));

        // icon tile
        int tile = 20;
        int tx = r.x + 7;
        int ty = r.y + 7;
        RenderUtil.roundedRect(context, tx, ty, tile, tile, 5, Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.ACCENT, e), t));
        RenderUtil.roundedRect(context, tx, ty, tile, tile / 2f, 5, Theme.withAlpha(0x14FFFFFF, t));
        String[] icon = Icons.forModule(m);
        if (icon != null) Icons.drawCentered(context, icon, tx, ty, tile, tile, Theme.withAlpha(Theme.lerp(0xFFB8B5E0, 0xFFFFFFFF, e), t));

        drawSwitch(context, r.x + r.w - 27, r.y + 12, e, t);

        boolean hasSettings = !m.getSettings().isEmpty();
        if (hasSettings) {
            Rect gear = gearRect(r);
            Animation gearHover = anim(m, "gear", 0f, HOVER_SPEED);
            gearHover.setTarget(hovered && gear.contains(mouseX, mouseY) ? 1f : 0f);
            float gh = gearHover.update();
            if (gh > 0.01f) RenderUtil.roundedRect(context, gear.x, gear.y, gear.w, gear.h, 3, Theme.withAlpha(0x22FFFFFF, t * gh));
            Icons.drawCentered(context, Icons.GEAR, gear.x, gear.y, gear.w, gear.h,
                    Theme.withAlpha(Theme.lerp(Theme.lerp(0xFF5A5788, Theme.MUTED, h), Theme.ACCENT_2, gh), t));
        }

        if (!Theme.visible(t)) return;
        if (icon == null) {
            String initial = m.getName().substring(0, 1).toUpperCase(Locale.ROOT);
            context.drawText(textRenderer, initial, tx + (tile - textRenderer.getWidth(initial)) / 2 + 1, ty + 6, Theme.withAlpha(0xFFFFFFFF, t), false);
        }
        String name = trimmed(m.getName(), r.w - 33 - 30);
        context.drawText(textRenderer, name, tx + tile + 6, r.y + 8, Theme.withAlpha(0xFFFFFFFF, t), false);
        context.drawText(textRenderer, m.isEnabled() ? "Enabled" : "Disabled", tx + tile + 6, r.y + 18,
                Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.ACCENT_2, e), t), false);
        String desc = trimmed(m.getDescription(), r.w - 14 - (hasSettings ? 16 : 0));
        context.drawText(textRenderer, desc, r.x + 7, r.y + r.h - 14, Theme.withAlpha(Theme.MUTED, t), false);
    }

    private String trimmed(String text, int maxWidth) {
        if (textRenderer.getWidth(text) <= maxWidth) return text;
        return textRenderer.trimToWidth(text, maxWidth - textRenderer.getWidth("…")) + "…";
    }

    private void drawSwitch(DrawContext context, int x, int y, float k, float t) {
        int sw = 20;
        int sh = 10;
        RenderUtil.roundedRect(context, x, y, sw, sh, sh / 2f, Theme.withAlpha(Theme.lerp(OFF_BAR, Theme.ACCENT, k), t));
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
        if (target != null) hint = "Enter to " + (target.isEnabled() ? "disable " : "enable ") + target.getName();
        else if (settingsFor == null) hint = "Click: toggle · Right-click: settings";
        else hint = "Right-click: previous option";
        hint = trimmed(hint, right - closeW - 12 - x);
        context.drawText(textRenderer, hint, x, y, Theme.withAlpha(target != null ? Theme.ACCENT_2 : Theme.MUTED, t), false);
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
        RenderUtil.roundedRect(context, back.x, back.y, back.w, back.h, 5, Theme.withAlpha(Theme.lerp(CARD_HOVER, Theme.ACCENT, backHover.update()), t));
        Icons.drawCentered(context, Icons.BACK, back.x, back.y, back.w, back.h, Theme.withAlpha(0xFFFFFFFF, t));
        String[] icon = Icons.forModule(settingsFor);
        int titleX = back.x + back.w + 8;
        if (icon != null) {
            Icons.draw(context, icon, titleX, py + 13, Theme.withAlpha(Theme.ACCENT_2, t));
            titleX += Icons.SIZE + 6;
        }
        if (text) {
            context.drawText(textRenderer, settingsFor.getName(), titleX, py + 14, Theme.withAlpha(0xFFFFFFFF, t), false);
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

        if (max > 0) {
            int trackY = area.y + SETTINGS_TOP;
            int trackH = area.h - SETTINGS_TOP;
            int thumbH = Math.max(16, Math.round(trackH * trackH / (trackH + max)));
            float thumbY = trackY + (trackH - thumbH) * (settingsScroll.get() / max);
            RenderUtil.roundedRect(context, area.x + area.w + 4, trackY, 2, trackH, 1, Theme.withAlpha(0x14FFFFFF, t));
            RenderUtil.roundedRect(context, area.x + area.w + 4, thumbY, 2, thumbH, 1, Theme.withAlpha(Theme.ACCENT, t));
        }
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

    private void renderModeRow(DrawContext context, Rect row, ModeSetting mode, int mouseX, int mouseY, float t) {
        float hover = rowHover(mode, row, mouseX, mouseY);
        RenderUtil.roundedRect(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, hover), t));
        int valueW = textRenderer.getWidth(mode.get());
        int rightArrowX = row.x + row.w - 8 - 3;
        int valueX = rightArrowX - 6 - valueW;
        int leftArrowX = valueX - 6 - 3;
        int arrowY = row.y + (row.h - 5) / 2;
        int arrow = Theme.withAlpha(Theme.lerp(Theme.MUTED, Theme.ACCENT_2, hover), t);
        Icons.draw(context, Icons.LEFT, leftArrowX, arrowY, arrow);
        Icons.draw(context, Icons.RIGHT, rightArrowX, arrowY, arrow);
        if (!Theme.visible(t)) return;
        context.drawText(textRenderer, mode.getName(), row.x + 8, row.y + (row.h - 8) / 2, Theme.withAlpha(Theme.TEXT, t), false);
        context.drawText(textRenderer, mode.get(), valueX, row.y + (row.h - 8) / 2, Theme.withAlpha(0xFFFFFFFF, t), false);
    }

    private void renderSwitchRow(DrawContext context, Rect row, String label, boolean value, Object key, int mouseX, int mouseY, float t) {
        float hover = rowHover(key, row, mouseX, mouseY);
        RenderUtil.roundedRect(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, hover), t));
        Animation knob = anim(key, "knob", value ? 1f : 0f, TOGGLE_SPEED);
        knob.setTarget(value ? 1f : 0f);
        drawSwitch(context, row.x + row.w - 28, row.y + (row.h - 10) / 2, knob.update(), t);
        if (Theme.visible(t)) context.drawText(textRenderer, label, row.x + 8, row.y + (row.h - 8) / 2, Theme.withAlpha(Theme.TEXT, t), false);
    }

    private void renderSliderRow(DrawContext context, Rect row, NumberSetting num, int mouseX, int mouseY, float t) {
        float hover = Math.max(rowHover(num, row, mouseX, mouseY), num == activeSlider ? 1f : 0f);
        RenderUtil.roundedRect(context, row.x, row.y, row.w, row.h, 5, Theme.withAlpha(Theme.lerp(CARD_BG, CARD_HOVER, hover), t));
        if (Theme.visible(t)) {
            context.drawText(textRenderer, num.getName(), row.x + 8, row.y + 6, Theme.withAlpha(Theme.TEXT, t), false);
            String value = num.format();
            context.drawText(textRenderer, value, row.x + row.w - 8 - textRenderer.getWidth(value), row.y + 6, Theme.withAlpha(Theme.ACCENT_2, t), false);
        }
        int trackX = row.x + 8;
        int trackW = row.w - 16;
        int trackY = row.y + 20;
        float frac = (float) num.getFraction();
        RenderUtil.roundedRect(context, trackX, trackY, trackW, 4, 2, Theme.withAlpha(OFF_BAR, t));
        RenderUtil.horizontalGradient(context, trackX + 1, trackY + 1, Math.round(trackX + trackW * frac), trackY + 3,
                Theme.withAlpha(Theme.ACCENT, t), Theme.withAlpha(Theme.ACCENT_2, t));
        float knobR = 3.5f + hover;
        RenderUtil.circle(context, trackX + trackW * frac, trackY + 2, knobR, Theme.withAlpha(0xFFFFFFFF, t));
    }

    // ── Input ───────────────────────────────────────────────────────────

    private void selectTab(Category c) {
        if (c != tab || settingsFor != null) pageIn();
        tab = c;
        lastTab = c;
        settingsFor = null;
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

        List<Category> tabs = tabs();
        for (int i = 0; i < tabs.size(); i++) {
            if (sidebarRow(i).contains(mouseX, mouseY)) {
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
                if (hasSettings && (button == 1 || gearRect(r).contains(mouseX, mouseY))) openSettings(m);
                else if (button == 0) m.toggle();
                return true;
            }
            return true;
        }

        if (backButton().contains(mouseX, mouseY)) {
            closeSettings();
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
            List<Category> tabs = tabs();
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
