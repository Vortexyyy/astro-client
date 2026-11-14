package dev.astroclient.gui.title;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.compat.Platform;
import dev.astroclient.gui.custom.CustomizeScreen;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.PixelFont;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Starfield;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.servers.ServersScreen;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.worlds.WorldsScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerWarningScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces vanilla's TitleScreen (see MinecraftClientMixin): menu column on the left, the signed-in
 * account's 3D skin on the right (drag to rotate), background from the player's theme.
 */
public class AstroTitleScreen extends AstroScreen {
    private static final int BUTTON_W = 170;
    private static final int BUTTON_H = 20;
    private static final int GAP = 4;
    private static final int BUTTONS = 5;
    private static final int FOOTER = 16;
    private static final int PLATE_H = 30;
    private static final String COPYRIGHT = "Copyright Mojang AB. Do not distribute!";

    // -- Tunables --
    /** entrance speed for the logo, buttons and player (higher = quicker) */
    private static final float INTRO_SPEED = 3.5f;
    /** how quickly the stars follow the pointer */
    private static final float POINTER_SPEED = 4f;
    /** GUI px the player model slides in from */
    private static final int MODEL_SLIDE = 40;
    /** darkening behind the menu column so it stays readable over picture backgrounds (0..1) */
    private static final float SCRIM = 0.55f;

    private final Animation intro = new Animation(0f, INTRO_SPEED);
    private final Animation pointerX = new Animation(0.5f, POINTER_SPEED);
    private final Animation pointerY = new Animation(0.5f, POINTER_SPEED);
    private int modelBaseX;
    private final List<AstroButton> buttons = new ArrayList<>();
    private final String versionLine;
    private ClickableWidget model;
    private int modelW;
    private int modelH;
    private int logoScale;
    private int logoY;

    public AstroTitleScreen() {
        super(Text.literal("Astro Client"));
        String modVersion = FabricLoader.getInstance().getModContainer(AstroClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        this.versionLine = "Astro Client " + modVersion.replaceFirst("\\+mc.*$", "") + "  ·  Minecraft " + Platform.minecraftVersion();
    }

    private boolean showModel() {
        return ThemeSettings.get().showPlayer && width >= 400 && height >= 180;
    }

    private int logoHeight() {
        return PixelFont.height(logoScale) + 6 + PixelFont.height(Math.max(1, logoScale / 3));
    }

    @Override
    protected void init() {
        buttons.clear();
        int x = Math.max(24, width / 10);
        // logo + buttons, centred in the space above the footer and scaled down on small windows
        int buttonsH = BUTTONS * BUTTON_H + (BUTTONS - 1) * GAP;
        logoScale = Math.max(2, Math.min(6, Math.min(width / 110, (height - FOOTER - buttonsH - 40) / 12)));
        int block = logoHeight() + 16 + buttonsH;
        logoY = Math.max(8, (height - FOOTER - block) / 2);
        int y = logoY + logoHeight() + 16;

        add(x, y, "Singleplayer", () -> client.setScreen(new WorldsScreen(this))).primary();
        AstroButton multiplayer = add(x, y += BUTTON_H + GAP, "Multiplayer", () -> client.setScreen(client.options.skipMultiplayerWarning
                ? new ServersScreen(this) : new MultiplayerWarningScreen(this)));
        multiplayer.active = client.isMultiplayerEnabled();
        add(x, y += BUTTON_H + GAP, "Options", () -> client.setScreen(new OptionsScreen(this, client.options)));
        add(x, y += BUTTON_H + GAP, "Customize", () -> client.setScreen(new CustomizeScreen(this)));
        add(x, y + BUTTON_H + GAP, "Quit Game", client::scheduleStop);

        if (showModel()) {
            modelH = Math.min(190, height - FOOTER - PLATE_H - 24);
            modelW = Math.round(modelH * 0.58f);
            model = Platform.playerModel(modelW, modelH);
            modelBaseX = width - Math.max(30, width / 8) - modelW;
            model.setX(modelBaseX);
            model.setY(Math.max(8, (height - FOOTER - modelH - PLATE_H - 8) / 2));
            addDrawableChild(model);
        } else {
            model = null;
        }
        intro.setTarget(1f);
    }

    private AstroButton add(int x, int y, String label, Runnable action) {
        AstroButton button = new AstroButton(x, y, BUTTON_W, BUTTON_H, Text.literal(label), b -> action.run()).leftAligned();
        buttons.add(button);
        return addDrawableChild(button);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float t = intro.update();
        pointerX.setTarget(mouseX / (float) Math.max(1, width));
        pointerY.setTarget(mouseY / (float) Math.max(1, height));
        Starfield.pointer(pointerX.update(), pointerY.update());
        if (model != null) model.setX(modelBaseX + Math.round((1f - Animation.easeOutCubic(t)) * MODEL_SLIDE));
        for (int i = 0; i < buttons.size(); i++) {
            float local = MathHelper.clamp(t * 1.6f - i * 0.12f, 0f, 1f);
            buttons.get(i).setIntro((1f - Animation.easeOutCubic(local)) * 14f, local);
        }
        super.render(context, mouseX, mouseY, delta); // background + widgets (incl. the player model)

        if (model != null && Theme.visible(t)) {
            String name = client.getSession().getUsername();
            int cx = model.getX() + modelW / 2;
            int plateY = model.getY() + modelH + 6;
            int plateW = Math.max(90, textRenderer.getWidth(name) + 24);
            RenderUtil.surface(context, cx - plateW / 2f, plateY, plateW, PLATE_H, Theme.R_LG, Theme.withAlpha(Theme.PANEL, 0.85f * t),
                    Theme.withAlpha(ThemeSettings.get().sharp() ? Theme.BORDER : Theme.BORDER_STRONG, t));
            context.drawCenteredTextWithShadow(textRenderer, name, cx, plateY + 6, Theme.withAlpha(Theme.TEXT, t));
            context.drawCenteredTextWithShadow(textRenderer, "Drag to rotate", cx, plateY + 18, Theme.withAlpha(Theme.MUTED, t));
        }

        if (Theme.visible(t)) {
            context.drawTextWithShadow(textRenderer, versionLine, 4, height - 12, Theme.withAlpha(Theme.MUTED, t));
            context.drawTextWithShadow(textRenderer, COPYRIGHT, width - textRenderer.getWidth(COPYRIGHT) - 4, height - 12,
                    Theme.withAlpha(Theme.MUTED, t));
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        Backgrounds.draw(context, ThemeSettings.get().menuBackground, width, height);

        float t = Animation.easeOutCubic(intro.get());
        int x = Math.max(24, width / 10);
        boolean nebula = !ThemeSettings.get().sharp();
        if (model != null && nebula && ThemeSettings.STARFIELD.equals(ThemeSettings.get().menuBackground)) {
            aurora(context, model.getX() + modelW / 2, model.getY() + modelH - 6, t);
        }
        RenderUtil.horizontalGradient(context, 0, 0, x + BUTTON_W + 90, height, Theme.withAlpha(0xFF05040F, SCRIM * t), 0x0005040F);
        int scale = logoScale;
        int logoY = this.logoY - Math.round((1f - t) * 10f);

        int subScale = Math.max(1, scale / 3);
        if (ThemeSettings.get().sharp()) {
            // Orbit: flat white wordmark, accent rule and a muted caption under it
            PixelFont.draw(context, "ASTRO", x, logoY, scale, scale * 2, Theme.withAlpha(Theme.TEXT, t), Theme.withAlpha(Theme.TEXT, t));
            int ruleY = logoY + PixelFont.height(scale) + 6;
            RenderUtil.roundedRect(context, x + 1, ruleY + PixelFont.height(subScale) / 2f - 0.5f, 12, 1.5f, 0, Theme.withAlpha(Theme.ACCENT, t));
            PixelFont.draw(context, "CLIENT", x + 18, ruleY, subScale, subScale * 3, Theme.withAlpha(Theme.MUTED, t), Theme.withAlpha(Theme.MUTED, t));
        } else {
            float clock = Animation.clock();
            float pulse = 0.5f + 0.5f * MathHelper.sin(clock * 1.7f);
            int logoW = PixelFont.width("ASTRO", scale, scale * 2);
            RenderUtil.glow(context, x, logoY, logoW, PixelFont.height(scale), 8,
                    Theme.withAlpha(Theme.ACCENT, (0.3f + 0.16f * pulse) * t), 20);
            // extruded wordmark: a deep violet copy below-right gives it depth, then a band of light sweeps across
            int depth = Math.max(1, scale / 2);
            PixelFont.draw(context, "ASTRO", x + depth, logoY + depth, scale, scale * 2,
                    Theme.withAlpha(Theme.lerp(Theme.ACCENT, Theme.SPACE_BOTTOM, 0.6f), t), Theme.withAlpha(Theme.lerp(Theme.ACCENT_2, Theme.SPACE_BOTTOM, 0.68f), t));
            float sheen = (clock * 0.42f) % 4.5f - 0.3f;
            PixelFont.draw(context, "ASTRO", x, logoY, scale, scale * 2, Theme.withAlpha(0xFFFFFFFF, t),
                    Theme.withAlpha(Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.12f), t), sheen);
            int capY = logoY + PixelFont.height(scale) + depth + 6;
            PixelFont.draw(context, "CLIENT", x + 2, capY, subScale, subScale * 4,
                    Theme.withAlpha(Theme.ACCENT_2, t), Theme.withAlpha(Theme.lerp(Theme.ACCENT_2, Theme.ACCENT, 0.55f), t));
            // the caption runs out into the Nebula accent line
            int capW = PixelFont.width("CLIENT", subScale, subScale * 4);
            RenderUtil.shimmerLine(context, x + 2 + capW + 8, capY + PixelFont.height(subScale) / 2, x + Math.max(logoW + depth, BUTTON_W), 1, t);
        }

        if (model != null) {
            // soft glowing platform under the player
            int cx = model.getX() + modelW / 2;
            int footY = model.getY() + modelH - 6;
            if (ThemeSettings.get().sharp()) {
                // Orbit: a hairline horizon with an accent marker under the player
                RenderUtil.roundedRect(context, cx - 60, footY + 2, 120, 1, 0, Theme.withAlpha(Theme.BORDER_STRONG, t));
                RenderUtil.roundedRect(context, cx - 3, footY, 6, 5, 0, Theme.withAlpha(Theme.ACCENT, t));
            } else {
                platform(context, cx, footY, t);
            }
        }
    }

    /**
     * Aurora behind the player: narrow columns of vertical gradients (violet at the horizon, cyan above)
     * whose height and brightness drift slowly. Effects quality Low halves the columns, Off skips it.
     */
    private void aurora(DrawContext context, int cx, int baseY, float t) {
        ThemeSettings.Quality q = ThemeSettings.get().effects;
        if (q == ThemeSettings.Quality.OFF) return;
        int col = q == ThemeSettings.Quality.HIGH ? 5 : 10;
        int half = Math.round(modelW * 1.3f);
        int reach = Math.min(modelH + 30, baseY - 4);
        float clock = Animation.clock();
        int upper = Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, 0.8f);
        for (int x0 = cx - half; x0 < cx + half; x0 += col) {
            float u = (x0 + col / 2f - cx) / half;
            float env = 1f - u * u;
            env *= env;
            float wave = 0.62f + 0.22f * MathHelper.sin(u * 3.1f + clock * 0.33f) + 0.16f * MathHelper.sin(u * 7.7f - clock * 0.21f + 1.7f);
            float a = 0.52f * env * (0.72f + 0.28f * MathHelper.sin(u * 5.3f + clock * 0.47f + 0.6f)) * t;
            if (a < 0.012f) continue;
            int h = Math.round(reach * wave * (0.4f + 0.6f * env));
            int peak = baseY - 8;
            int mid = peak - Math.round(h * 0.4f);
            int violet = Theme.withAlpha(Theme.ACCENT, a);
            int cyan = Theme.withAlpha(upper, a * 0.6f);
            context.fillGradient(x0, peak - h, x0 + col, mid, cyan & 0x00FFFFFF, cyan);
            context.fillGradient(x0, mid, x0 + col, peak, cyan, violet);
            context.fillGradient(x0, peak, x0 + col, baseY + 8, violet, violet & 0x00FFFFFF);
        }
    }

    /** Glowing platform under the player with a soft light beam and two dotted orbits, one carrying a comet. */
    private void platform(DrawContext context, int cx, int footY, float t) {
        ThemeSettings.Quality q = ThemeSettings.get().effects;
        float clock = Animation.clock();
        if (q != ThemeSettings.Quality.OFF) {
            // light rising from the disc: nested gradient quads so the sides fall off softly
            int beam = Theme.withAlpha(Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, 0.35f), 0.075f * t);
            for (int i = 0; i < 4; i++) {
                int half = 46 - i * 10;
                context.fillGradient(cx - half, footY - 92 + i * 12, cx + half, footY, beam & 0x00FFFFFF, beam);
            }
            orbit(context, cx, footY + 1, 98, 17, -clock * 0.22f + 2f, false, 0.55f * t);
            orbit(context, cx, footY + 1, 72, 12, clock * 0.55f, true, t);
        }
        float pulse = 0.5f + 0.5f * MathHelper.sin(clock * 1.3f);
        RenderUtil.glow(context, cx - 46, footY - 4, 92, 9, 4.5f, Theme.withAlpha(Theme.ACCENT_2, (0.5f + 0.2f * pulse) * t), 12);
        RenderUtil.gradientRect(context, cx - 46, footY - 4, 92, 9, 4.5f, Theme.withAlpha(Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.3f), t),
                Theme.withAlpha(Theme.lerp(Theme.ACCENT, Theme.SPACE_BOTTOM, 0.45f), t));
        RenderUtil.roundedRect(context, cx - 36, footY - 3, 72, 1, 0.5f, Theme.withAlpha(Theme.lerp(Theme.ACCENT_2, 0xFFFFFFFF, 0.4f), 0.9f * t));
    }

    /** A dotted ellipse; the near side is brighter, and the comet (if any) lights the dots it has just passed. */
    private void orbit(DrawContext context, int cx, int cy, int rx, int ry, float angle, boolean comet, float alpha) {
        int dots = ThemeSettings.get().effects == ThemeSettings.Quality.HIGH ? 84 : 42;
        float head = MathHelper.floorMod(angle, MathHelper.TAU);
        int rgb = Theme.lerp(Theme.ACCENT, Theme.ACCENT_2, comet ? 0.7f : 0.2f) & 0x00FFFFFF;
        for (int i = 0; i < dots; i++) {
            float a = MathHelper.TAU * i / dots;
            float sin = MathHelper.sin(a);
            float strength = 0.16f + 0.26f * (0.5f + 0.5f * sin);
            if (comet) {
                float behind = MathHelper.floorMod(head - a, MathHelper.TAU);
                if (behind < 1.5f) strength += (1f - behind / 1.5f) * (1f - behind / 1.5f) * 0.75f;
            }
            int al = Math.round(255 * Math.min(1f, strength) * alpha);
            if (al < 6) continue;
            int px = cx + Math.round(MathHelper.cos(a) * rx);
            int py = cy + Math.round(sin * ry);
            context.fill(px, py, px + 1, py + 1, (al << 24) | rgb);
        }
        if (comet) {
            float hx = cx + MathHelper.cos(head) * rx, hy = cy + MathHelper.sin(head) * ry;
            RenderUtil.glow(context, hx - 1.5f, hy - 1.5f, 3, 3, 1.5f, Theme.withAlpha(Theme.ACCENT_2, 0.8f * alpha), 4);
            RenderUtil.circle(context, hx, hy, 1.5f, Theme.withAlpha(0xFFFFFFFF, alpha));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
