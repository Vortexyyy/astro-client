package dev.astroclient.gui.render;

import dev.astroclient.module.Category;
import dev.astroclient.module.Module;
import net.minecraft.client.gui.DrawContext;

import java.util.Map;

/**
 * 9×9 pixel-art icons for the mod menu. Drawn with fills (one per horizontal run), so they stay
 * crisp at every GUI scale and don't depend on which glyphs the Minecraft font happens to have.
 */
public final class Icons {
    public static final int SIZE = 9;

    public static final String[] GEAR = {
            "...###...", ".#.###.#.", ".#######.", "###...###", "###...###", "###...###", ".#######.", ".#.###.#.", "...###..."};
    public static final String[] SEARCH = {
            "..###....", ".#...#...", "#.....#..", "#.....#..", "#.....#..", ".#...#...", "..####...", ".....###.", "......###"};
    public static final String[] PENCIL = {
            "......##.", ".....#..#", "....#..#.", "...#..#..", "..#..#...", ".#..#....", "#..#.....", "###......", "##......."};
    public static final String[] BACK = {
            "....#", "...#.", "..#..", ".#...", "#....", ".#...", "..#..", "...#.", "....#"};
    public static final String[] STAR = {
            "....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..#####..", ".###.###.", ".#.....#."};
    public static final String[] RESET = {
            "..####...", ".#....#.#", "#......##", "#.....###", "#........", "#........", "#......#.", ".#....#..", "..####..."};
    public static final String[] LEFT = {"..#", ".#.", "#..", ".#.", "..#"};
    public static final String[] RIGHT = {"#..", ".#.", "..#", ".#.", "#.."};

    private static final String[] GRID = {
            "####.####", "####.####", "####.####", "####.####", ".........", "####.####", "####.####", "####.####", "####.####"};
    private static final String[] SPRINT = {
            ".........", "#...#....", ".#...#...", "..#...#..", "...#...#.", "..#...#..", ".#...#...", "#...#....", "........."};
    private static final String[] EYE = {
            ".........", "...###...", ".##...##.", "#...#...#", "#..###..#", "#...#...#", ".##...##.", "...###...", "........."};
    private static final String[] LAYOUT = {
            ".........", "#########", "#.......#", "#.##....#", "#.......#", "#.......#", "#....##.#", "#.......#", "#########"};
    private static final String[] DOTS = {
            ".........", ".........", ".........", "##..#..##", "##.###.##", "##..#..##", ".........", ".........", "........."};
    private static final String[] ZOOM = {
            "..###....", ".#...#...", "#..#..#..", "#.###.#..", "#..#..#..", ".#...#...", "..####...", ".....###.", "......###"};
    private static final String[] SUN = {
            "....#....", ".#.....#.", "...###...", "..#####..", "#.#####.#", "..#####..", "...###...", ".#.....#.", "....#...."};
    private static final String[] CROSSHAIR = {
            "....#....", "....#....", ".........", ".........", "##..#..##", ".........", ".........", "....#....", "....#...."};
    private static final String[] MONITOR = {
            "#########", "#.......#", "#.....#.#", "#....#..#", "#.#.#...#", "#..#....#", "#########", "...###...", "..#####.."};
    private static final String[] MOUSE = {
            "..#####..", ".#..#..#.", ".#..#..#.", ".#######.", ".#.....#.", ".#.....#.", ".#.....#.", "..#####..", "........."};
    private static final String[] PIN = {
            "...###...", "..#...#..", ".#..#..#.", ".#.###.#.", ".#..#..#.", "..#...#..", "..#...#..", "...#.#...", "....#...."};
    private static final String[] SIGNAL = {
            ".......##", ".......##", ".......##", "....##.##", "....##.##", "....##.##", ".##.##.##", ".##.##.##", ".##.##.##"};
    private static final String[] KEYS = {
            ".........", "...###...", "...#.#...", "...###...", ".........", "#########", "#.##.##.#", "#########", "........."};
    private static final String[] ARMOR = {
            ".##...##.", "###...###", "#########", ".#######.", ".#######.", ".#######.", ".#######.", ".#######.", "........."};
    private static final String[] POTION = {
            "...###...", "....#....", "....#....", "...###...", "..#...#..", ".#.....#.", ".#######.", ".#######.", "..#####.."};

    private static final Map<String, String[]> MODULES = Map.ofEntries(
            Map.entry("Toggle Sprint", SPRINT),
            Map.entry("Freelook", EYE),
            Map.entry("Zoom", ZOOM),
            Map.entry("Fullbright", SUN),
            Map.entry("Crosshair", CROSSHAIR),
            Map.entry("FPS", MONITOR),
            Map.entry("CPS", MOUSE),
            Map.entry("Coordinates", PIN),
            Map.entry("Ping", SIGNAL),
            Map.entry("Keystrokes", KEYS),
            Map.entry("Armor", ARMOR),
            Map.entry("Potions", POTION));

    private Icons() {}

    /** Icon for a module, or null when it has none (callers fall back to its initial). */
    public static String[] forModule(Module module) {
        return MODULES.get(module.getName());
    }

    public static String[] forCategory(Category category) {
        if (category == null) return GRID;
        return switch (category) {
            case MOVEMENT -> SPRINT;
            case RENDER -> EYE;
            case HUD -> LAYOUT;
            case MISC -> DOTS;
        };
    }

    public static int width(String[] icon) {
        return icon[0].length();
    }

    public static void draw(DrawContext context, String[] icon, int x, int y, int color) {
        for (int row = 0; row < icon.length; row++) {
            String line = icon[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') col++;
                context.fill(x + start, y + row, x + col, y + row + 1, color);
            }
        }
    }

    /** Draws the icon centred in a w×h box. */
    public static void drawCentered(DrawContext context, String[] icon, int x, int y, int w, int h, int color) {
        draw(context, icon, x + (w - width(icon)) / 2, y + (h - icon.length) / 2, color);
    }
}
