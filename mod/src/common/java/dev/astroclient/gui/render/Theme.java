package dev.astroclient.gui.render;

import net.minecraft.util.math.MathHelper;

/**
 * Astro palette (ARGB) shared by the splash, menus and HUD: the "Orbit" look, graphite surfaces with
 * hairline borders and one accent. The accent pair changes with the preset chosen in Customize
 * (see {@link #apply}); everything else is fixed.
 */
public final class Theme {
    public static final int SPACE_TOP = 0xFF0A0B10;
    public static final int SPACE_BOTTOM = 0xFF05060A;
    public static final int PANEL = 0xF00B0C12;
    public static final int PANEL_HEADER = 0xFF12141C;
    public static final int ROW_HOVER = 0x14FFFFFF;
    public static final int HUD_BACKGROUND = 0xD60A0B10;
    public static final int TEXT = 0xFFECEEF3;
    public static final int MUTED = 0xFF8D93A5;
    public static final int TRACK = 0xFF2B3040;
    /** Deep surface behind full-screen panels (menus, customize, worlds, servers). */
    public static final int PANEL_DEEP = 0xF50B0C12;
    public static final int CARD = 0xFF12141C;
    public static final int CARD_HOVER = 0xFF181B26;
    /** Hairline borders around panels, cards, keys and pills. */
    public static final int BORDER = 0xFF2B3040;
    public static final int BORDER_STRONG = 0xFF3D4458;

    // Status colours, shared so armour, potions and toggles agree
    public static final int GOOD = 0xFF34D399;
    public static final int WARN = 0xFFFFB23D;
    public static final int BAD = 0xFFFF6B3D;

    // Corner radii (GUI px) for the Rounded corner style; Sharp (the default) draws them square
    /** labels, tags, scrollbars */
    public static final float R_SM = 3f;
    /** HUD pills, keys, rows, inputs */
    public static final float R_MD = 5f;
    /** cards */
    public static final float R_LG = 8f;
    /** full panels */
    public static final float R_XL = 10f;

    /** Primary accent (Orbit: acid lime). */
    public static int ACCENT = 0xFFD4FF3A;
    /** Secondary accent, for highlights next to the primary one. */
    public static int ACCENT_2 = 0xFFECFF9E;
    /** Text drawn on an ACCENT fill: dark on light accents, white on dark ones. */
    public static int ON_ACCENT = 0xFF0A0B10;

    public enum Preset {
        ORBIT("Orbit", 0xFFD4FF3A, 0xFFECFF9E),
        SIGNAL("Signal", 0xFF3DE0FF, 0xFFB8F3FF),
        FLARE("Flare", 0xFFFF5CA8, 0xFFFFB8D9),
        AMBER("Amber", 0xFFFFB23D, 0xFFFFE0A3),
        NEBULA("Nebula", 0xFF8B5CF6, 0xFF22D3EE),
        AURORA("Aurora", 0xFF10B981, 0xFF5EEAD4),
        SOLAR("Solar", 0xFFF97316, 0xFFFACC15),
        CRIMSON("Crimson", 0xFFF43F5E, 0xFFF0ABFC),
        FROST("Frost", 0xFF3B82F6, 0xFFBAE6FD),
        MONO("Mono", 0xFF71717A, 0xFFF4F4F5);

        public final String label;
        public final int accent;
        public final int accent2;

        Preset(String label, int accent, int accent2) {
            this.label = label;
            this.accent = accent;
            this.accent2 = accent2;
        }
    }

    private Theme() {}

    public static void apply(Preset preset) {
        ACCENT = preset.accent;
        ACCENT_2 = preset.accent2;
        ON_ACCENT = luminance(ACCENT) > 0.45f ? 0xFF0A0B10 : 0xFFFFFFFF;
    }

    /** Relative luminance (0..1, sRGB approximation) for picking readable text colours. */
    public static float luminance(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        return 0.2126f * r * r + 0.7152f * g * g + 0.0722f * b * b;
    }

    /** Multiplies the colour's existing alpha by {@code alpha} (0..1). */
    public static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * MathHelper.clamp(alpha, 0f, 1f));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        t = MathHelper.clamp(t, 0f, 1f);
        int a = (int) MathHelper.lerp(t, from >>> 24, to >>> 24);
        int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Text colours whose alpha is below ~4 are treated as opaque by the vanilla text renderer,
     * so fading text must stop drawing before it gets there.
     */
    public static boolean visible(float alpha) {
        return alpha > 0.05f;
    }
}
