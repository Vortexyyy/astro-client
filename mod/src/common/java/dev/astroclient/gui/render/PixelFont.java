package dev.astroclient.gui.render;

import net.minecraft.client.gui.DrawContext;

import java.util.Map;

/**
 * A tiny 5×7 block font for the "ASTRO CLIENT" wordmark. The splash overlay is drawn while
 * fonts and textures are still loading, so the logo can't depend on either.
 */
public final class PixelFont {
    private static final int GLYPH_W = 5;
    private static final int GLYPH_H = 7;

    private static final Map<Character, String[]> GLYPHS = Map.of(
            'A', new String[] {".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"},
            'S', new String[] {".####", "#....", "#....", ".###.", "....#", "....#", "####."},
            'T', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."},
            'R', new String[] {"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"},
            'O', new String[] {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."},
            'C', new String[] {".####", "#....", "#....", "#....", "#....", "#....", ".####"},
            'L', new String[] {"#....", "#....", "#....", "#....", "#....", "#....", "#####"},
            'I', new String[] {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"},
            'E', new String[] {"#####", "#....", "#....", "####.", "#....", "#....", "#####"},
            'N', new String[] {"#...#", "##..#", "##..#", "#.#.#", "#..##", "#..##", "#...#"});

    private PixelFont() {}

    public static int width(String text, int scale, int spacing) {
        if (text.isEmpty()) return 0;
        return text.length() * (GLYPH_W * scale + spacing) - spacing;
    }

    public static int height(int scale) {
        return GLYPH_H * scale;
    }

    /** Draws text with a left-to-right gradient between two colours. Unknown characters render as spaces. */
    public static void draw(DrawContext context, String text, int x, int y, int scale, int spacing, int from, int to) {
        draw(context, text, x, y, scale, spacing, from, to, -1f);
    }

    /**
     * Same, with a band of light at {@code sheen} (0..1 across the text; outside that range there is none)
     * for the logo's sweep.
     */
    public static void draw(DrawContext context, String text, int x, int y, int scale, int spacing, int from, int to, float sheen) {
        boolean lit = sheen > -0.25f && sheen < 1.25f;
        int white = 0x00FFFFFF | (Math.max(from >>> 24, to >>> 24) << 24);
        int total = Math.max(1, width(text, scale, spacing));
        int cx = x;
        for (char c : text.toCharArray()) {
            String[] rows = GLYPHS.get(Character.toUpperCase(c));
            if (rows != null) {
                for (int row = 0; row < GLYPH_H; row++) {
                    String line = rows[row];
                    int col = 0;
                    while (col < GLYPH_W) {
                        if (line.charAt(col) != '#') {
                            col++;
                            continue;
                        }
                        int start = col;
                        while (col < GLYPH_W && line.charAt(col) == '#') col++;
                        int x1 = cx + start * scale;
                        int x2 = cx + col * scale;
                        float f = (x1 - x + (x2 - x1) / 2f) / total;
                        int color = Theme.lerp(from, to, f);
                        if (lit) {
                            // the band leans like italic light: lower rows are hit slightly later
                            float d = Math.abs(f - sheen + row * 0.012f) / 0.2f;
                            if (d < 1f) color = Theme.lerp(color, white, (1f - d) * 0.85f);
                        }
                        context.fill(x1, y + row * scale, x2, y + (row + 1) * scale, color);
                    }
                }
            }
            cx += GLYPH_W * scale + spacing;
        }
    }

    public static void drawCentered(DrawContext context, String text, int centerX, int y, int scale, int spacing, int from, int to) {
        draw(context, text, centerX - width(text, scale, spacing) / 2, y, scale, spacing, from, to);
    }
}
