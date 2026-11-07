package dev.astroclient.gui.render;

import net.minecraft.util.math.MathHelper;

/**
 * Astro palette (ARGB) shared by the splash, menus and HUD. The accent pair changes with the
 * theme preset chosen in Customize (see {@link #apply}); everything else is fixed.
 */
public final class Theme {
    public static final int SPACE_TOP = 0xFF0B0A24;
    public static final int SPACE_BOTTOM = 0xFF03040C;
    public static final int PANEL = 0xEE10132A;
    public static final int PANEL_HEADER = 0xFF171B3D;
    public static final int ROW_HOVER = 0x22FFFFFF;
    public static final int HUD_BACKGROUND = 0x9910132A;
    public static final int TEXT = 0xFFE8EAFF;
    public static final int MUTED = 0xFF8A8FB8;
    public static final int TRACK = 0xFF262A4D;

    /** Primary accent (Nebula: violet). */
    public static int ACCENT = 0xFF8B5CF6;
    /** Secondary accent (Nebula: cyan). */
    public static int ACCENT_2 = 0xFF22D3EE;

    public enum Preset {
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
