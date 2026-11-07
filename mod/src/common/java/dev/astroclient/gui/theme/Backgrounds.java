package dev.astroclient.gui.theme;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.Gfx;
import dev.astroclient.compat.Platform;
import dev.astroclient.gui.render.Starfield;
import dev.astroclient.gui.render.Theme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Background pictures for the menus: PNG files the player drops into config/astroclient/backgrounds.
 * Each file is uploaded once as a texture and drawn "cover"-scaled (fills the screen, keeps aspect).
 */
public final class Backgrounds {
    private static final Starfield STARS = new Starfield(240, 0xA57120L);
    private static final Map<String, Loaded> CACHE = new HashMap<>();
    private static Path dir;

    private record Loaded(Identifier id, int width, int height) {}

    private Backgrounds() {}

    public static void init(Path configDir) {
        dir = configDir.resolve("astroclient").resolve("backgrounds");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            AstroClient.LOGGER.warn("Could not create {}", dir, e);
        }
    }

    public static Path folder() {
        return dir;
    }

    public static void openFolder() {
        Util.getOperatingSystem().open(dir.toFile());
    }

    /** PNG file names available as backgrounds, sorted. */
    public static List<String> list() {
        List<String> names = new ArrayList<>();
        if (dir == null) return names;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .forEach(p -> names.add(p.getFileName().toString()));
        } catch (IOException e) {
            AstroClient.LOGGER.warn("Could not list {}", dir, e);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** Forgets loaded pictures so edited files are picked up again. */
    public static void reload() {
        for (Loaded l : CACHE.values()) {
            if (l != null) MinecraftClient.getInstance().getTextureManager().destroyTexture(l.id);
        }
        CACHE.clear();
    }

    private static Loaded load(String fileName) {
        return CACHE.computeIfAbsent(fileName, name -> {
            Path file = dir.resolve(name);
            if (!file.normalize().startsWith(dir) || !Files.isRegularFile(file)) return null;
            try (InputStream in = Files.newInputStream(file)) {
                NativeImage image = NativeImage.read(in);
                Identifier id = Identifier.of("astroclient", "background/" + Integer.toHexString(name.hashCode()));
                Platform.registerTexture(id, image);
                return new Loaded(id, image.getWidth(), image.getHeight());
            } catch (IOException | RuntimeException e) {
                AstroClient.LOGGER.warn("Background {} is not a readable PNG", name, e);
                return null;
            }
        });
    }

    public static String label(String background) {
        if (ThemeSettings.STARFIELD.equals(background)) return "Starfield";
        if (ThemeSettings.SAME.equals(background)) return "Same as main menu";
        return background;
    }

    /** Draws a background spec ("starfield" or a file name) over the whole screen. */
    public static void draw(DrawContext context, String background, int width, int height) {
        Loaded image = ThemeSettings.STARFIELD.equals(background) ? null : load(background);
        if (image == null) {
            Starfield.background(context, width, height, 1f);
            STARS.render(context, width, height, 1f);
            return;
        }
        float scale = Math.max((float) width / image.width, (float) height / image.height);
        Gfx.push(context);
        Gfx.translate(context, (width - image.width * scale) / 2f, (height - image.height * scale) / 2f);
        Gfx.scale(context, scale, scale);
        Gfx.texture(context, image.id, 0, 0, 0, 0, image.width, image.height, image.width, image.height);
        Gfx.pop(context);
        int dim = ThemeSettings.get().backgroundDim;
        if (dim > 0) context.fill(0, 0, width, height, Theme.withAlpha(0xFF000000, dim / 100f));
    }
}
