package dev.astroclient.gui.title;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.compat.Platform;
import dev.astroclient.gui.custom.CustomizeScreen;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.PixelFont;
import dev.astroclient.gui.render.RenderUtil;
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
import net.minecraft.util.Util;
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

    private final Animation intro = new Animation(0f, 3.5f);
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

        add(x, y, "Singleplayer", () -> client.setScreen(new WorldsScreen(this)));
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
            model.setX(width - Math.max(30, width / 8) - modelW);
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
            RenderUtil.roundedRect(context, cx - plateW / 2f, plateY, plateW, PLATE_H, 8, Theme.withAlpha(Theme.PANEL, 0.85f * t));
            context.drawCenteredTextWithShadow(textRenderer, name, cx, plateY + 6, Theme.withAlpha(0xFFFFFFFF, t));
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
        int scale = logoScale;
        int logoY = this.logoY - Math.round((1f - t) * 10f);

        float pulse = 0.5f + 0.5f * MathHelper.sin((Util.getMeasuringTimeMs() % 100_000L) / 600f);
        int logoW = PixelFont.width("ASTRO", scale, scale * 2);
        RenderUtil.glow(context, x, logoY, logoW, PixelFont.height(scale), 8,
                Theme.withAlpha(Theme.ACCENT, (0.25f + 0.15f * pulse) * t), 18);
        PixelFont.draw(context, "ASTRO", x, logoY, scale, scale * 2, Theme.withAlpha(0xFFFFFFFF, t), Theme.withAlpha(Theme.ACCENT, t));
        int subScale = Math.max(1, scale / 3);
        PixelFont.draw(context, "CLIENT", x + 2, logoY + PixelFont.height(scale) + 6, subScale, subScale * 4,
                Theme.withAlpha(Theme.ACCENT_2, t), Theme.withAlpha(Theme.ACCENT_2, t));

        if (model != null) {
            // soft glowing platform under the player
            int cx = model.getX() + modelW / 2;
            int footY = model.getY() + modelH - 6;
            RenderUtil.glow(context, cx - 45, footY - 4, 90, 8, 4, Theme.withAlpha(Theme.ACCENT_2, 0.5f * t), 10);
            RenderUtil.roundedRect(context, cx - 45, footY - 4, 90, 8, 4, Theme.withAlpha(Theme.ACCENT, 0.6f * t));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
