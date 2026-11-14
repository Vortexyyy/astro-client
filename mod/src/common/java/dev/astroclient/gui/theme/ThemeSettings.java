package dev.astroclient.gui.theme;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.astroclient.AstroClient;
import dev.astroclient.gui.render.Theme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Menu look-and-feel chosen in the Customize screen, saved to config/astroclient-theme.json.
 * Backgrounds are {@link #STARFIELD}, {@link #SAME} (only for the worlds/servers screens) or the
 * file name of a PNG in config/astroclient/backgrounds.
 */
public final class ThemeSettings {
    public static final String STARFIELD = "starfield";
    public static final String SAME = "same";

    public enum ButtonStyle {
        ROUNDED("Rounded"),
        PILL("Pill"),
        SHARP("Sharp");

        public final String label;

        ButtonStyle(String label) {
            this.label = label;
        }
    }

    /** Visual effects level: glow, gradients, starfield density and parallax. */
    public enum Quality {
        OFF("Off"),
        LOW("Low"),
        HIGH("High");

        public final String label;

        Quality(String label) {
            this.label = label;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ThemeSettings current = new ThemeSettings();
    private static Path file;

    // Saved fields (public for Gson)
    public boolean customMenus = true;
    public Theme.Preset preset = Theme.Preset.ORBIT;
    public String menuBackground = STARFIELD;
    public String worldsBackground = SAME;
    public String serversBackground = SAME;
    /** 0..80 % black over background pictures, so text stays readable */
    public int backgroundDim = 35;
    /** 40..100 % opacity of menu panels */
    public int panelOpacity = 85;
    public ButtonStyle buttonStyle = ButtonStyle.SHARP;
    /** Bumped when a redesign changes defaults; older files are migrated once in sanitized(). */
    public int styleVersion;
    public boolean showPlayer = true;
    /** Glow, gradient and starfield detail; lower it on weak hardware. */
    public Quality effects = Quality.HIGH;
    /** How often HUD text (FPS, coordinates, timers...) is rebuilt per second; 0 = every frame. */
    public int hudRefreshHz = 30;

    public static ThemeSettings get() {
        return current;
    }

    public static void load(Path configDir) {
        file = configDir.resolve("astroclient-theme.json");
        try {
            if (Files.exists(file)) {
                ThemeSettings loaded = GSON.fromJson(Files.readString(file), ThemeSettings.class);
                if (loaded != null) current = loaded.sanitized();
            }
        } catch (IOException | RuntimeException e) {
            AstroClient.LOGGER.warn("Could not read {}, using default theme", file, e);
        }
        Theme.apply(current.preset);
    }

    public static void save() {
        current.styleVersion = 2;
        Theme.apply(current.preset);
        if (file == null) return;
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(current));
        } catch (IOException e) {
            AstroClient.LOGGER.warn("Could not save {}", file, e);
        }
    }

    private ThemeSettings sanitized() {
        if (preset == null) preset = Theme.Preset.ORBIT;
        if (buttonStyle == null) buttonStyle = ButtonStyle.SHARP;
        if (styleVersion < 2) {
            // 0.4 "Orbit" redesign: move everyone to the new look once; they can switch back in Customize
            preset = Theme.Preset.ORBIT;
            buttonStyle = ButtonStyle.SHARP;
            styleVersion = 2;
        }
        if (menuBackground == null) menuBackground = STARFIELD;
        if (worldsBackground == null) worldsBackground = SAME;
        if (serversBackground == null) serversBackground = SAME;
        if (effects == null) effects = Quality.HIGH;
        if (hudRefreshHz < 0 || hudRefreshHz > 240) hudRefreshHz = 30;
        backgroundDim = Math.max(0, Math.min(80, backgroundDim));
        panelOpacity = Math.max(40, Math.min(100, panelOpacity));
        return this;
    }

    /** Resolves "same as main menu" for the worlds/servers screens. */
    public String resolve(String background) {
        return SAME.equals(background) ? menuBackground : background;
    }

    public float panelAlpha() {
        return panelOpacity / 100f;
    }

    /** Corner style for every Astro surface: Sharp draws all shapes square (the Orbit look). */
    public boolean sharp() {
        return buttonStyle == ButtonStyle.SHARP;
    }

    public float buttonRadius(int height) {
        return switch (buttonStyle) {
            case PILL -> height / 2f;
            case SHARP -> 0f;
            case ROUNDED -> 6f;
        };
    }
}
