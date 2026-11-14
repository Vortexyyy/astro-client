package dev.astroclient.bench;

import dev.astroclient.AstroClient;
import dev.astroclient.gui.title.AstroTitleScreen;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * Developer benchmark, off unless the ASTRO_BENCH environment variable is set (its value names the run).
 * Opens (or creates) a test world, waits for chunks to load, records frame times for a fixed window,
 * writes run/bench/&lt;name&gt;.json and quits. ASTRO_BENCH_HUD picks the HUD: "none", "default" or "all".
 * Nothing is saved to the player's Astro config while it runs.
 */
public final class Benchmark {
    private static final String NAME = System.getenv("ASTRO_BENCH");
    private static final String HUD = System.getenv().getOrDefault("ASTRO_BENCH_HUD", "default");
    private static final String WORLD = "astro-bench";
    private static final long WARMUP_NS = 20_000_000_000L;
    private static final long RECORD_NS = 30_000_000_000L;
    private static final int MAX_FRAMES = 2_000_000;

    private enum State { WAIT_TITLE, LOADING, WARMUP, RECORD, MENU_SHOT, DONE }
    /** ASTRO_SHOTS=1 also screenshots the title screen and the Right Shift menu, for visual checks. */
    private static final boolean SHOTS = System.getenv("ASTRO_SHOTS") != null;
    private static int titleTicks;

    private static State state = State.WAIT_TITLE;
    private static long stateStart;
    private static long lastFrame;
    private static long[] frameNs;
    private static long[] hudNs;
    private static int frames;
    private static long heapPeak;
    private static double cpuSum;
    private static int cpuSamples;
    private static long hudStart;

    private Benchmark() {}

    public static boolean active() {
        return NAME != null && !NAME.isBlank();
    }

    /** Called every client tick. */
    public static void tick(MinecraftClient client) {
        if (!active()) return;
        long now = System.nanoTime();
        switch (state) {
            case WAIT_TITLE -> {
                if (client.currentScreen instanceof AstroTitleScreen || client.currentScreen instanceof TitleScreen) {
                    if (SHOTS && ++titleTicks < 80) return; // let the intro animation finish
                    if (SHOTS && titleTicks == 80) {
                        shot(client);
                        return;
                    }
                    configure(client);
                    if (Files.isDirectory(client.runDirectory.toPath().resolve("saves").resolve(WORLD))) {
                        try {
                            client.createIntegratedServerLoader().start(WORLD, () -> {});
                        } catch (RuntimeException e) {
                            AstroClient.LOGGER.warn("[bench] world busy, retrying", e);
                            return; // stay in WAIT_TITLE and try again next tick
                        }
                    } else {
                        createTestWorld(client);
                    }
                    enter(State.LOADING, now);
                }
            }
            case LOADING -> {
                if (client.world == null && now - stateStart > 60_000_000_000L) {
                    // the world was busy (error screen) or never opened: back to the title screen and retry
                    client.setScreen(null);
                    enter(State.WAIT_TITLE, now);
                    return;
                }
                if (client.world != null && client.player != null) {
                    client.setScreen(null);
                    enter(State.WARMUP, now);
                }
            }
            case WARMUP -> {
                if (client.currentScreen != null) client.setScreen(null);
                if (now - stateStart > WARMUP_NS) {
                    // one screenshot for visual checks, taken before the measured window
                    net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), msg -> {});
                    frameNs = new long[MAX_FRAMES];
                    hudNs = new long[MAX_FRAMES];
                    frames = 0;
                    lastFrame = 0;
                    enter(State.RECORD, now);
                }
            }
            case RECORD -> {
                if (client.currentScreen != null) client.setScreen(null);
                sampleSystem();
                if (now - stateStart > RECORD_NS || frames >= MAX_FRAMES) {
                    write(client, now - stateStart);
                    if (SHOTS) {
                        client.setScreen(new dev.astroclient.gui.menu.ModMenuScreen());
                        enter(State.MENU_SHOT, now);
                        return;
                    }
                    enter(State.DONE, now);
                    client.scheduleStop();
                }
            }
            case MENU_SHOT -> {
                if (now - stateStart > 1_500_000_000L) {
                    shot(client);
                    enter(State.DONE, now);
                    client.scheduleStop();
                }
            }
            case DONE -> {}
        }
    }

    private static void shot(MinecraftClient client) {
        net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory, client.getFramebuffer(), msg -> {});
    }

    private static void enter(State s, long now) {
        state = s;
        stateStart = now;
        AstroClient.LOGGER.info("[bench] {}", s);
    }

    /** Fixed settings so runs are comparable: no vsync, unlimited FPS, render distance 12, chosen HUD. */
    private static void configure(MinecraftClient client) {
        client.options.getEnableVsync().setValue(false);
        client.options.getMaxFps().setValue(260);
        client.options.getViewDistance().setValue(12);
        client.options.pauseOnLostFocus = false;
        relaxInactivityLimit(client);
        for (Module m : AstroClient.get().modules().all()) {
            boolean on = switch (HUD) {
                case "none" -> false;
                case "all" -> m instanceof HudModule;
                default -> m.getName().equals("FPS") || m.getName().equals("Keystrokes") || m.getName().equals("CPS");
            };
            m.setEnabled(on);
        }
    }

    /** 1.21.2+ drops to 30 FPS after a minute without input ("AFK"); only throttle when minimised. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void relaxInactivityLimit(MinecraftClient client) {
        try {
            Object option = client.options.getClass().getMethod("getInactivityFpsLimit").invoke(client.options);
            Class<?> type = Class.forName("net.minecraft.client.option.InactivityFpsLimit");
            Object minimized = Enum.valueOf((Class) type, "MINIMIZED");
            ((net.minecraft.client.option.SimpleOption) option).setValue(minimized);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // older versions have no inactivity limit
        }
    }

    private static void createTestWorld(MinecraftClient client) {
        try {
            Class.forName("net.minecraft.client.gui.screen.world.CreateWorldScreen")
                    .getMethod("showTestWorld", MinecraftClient.class, Runnable.class)
                    .invoke(null, client, (Runnable) () -> {});
        } catch (ReflectiveOperationException e) {
            AstroClient.LOGGER.error("[bench] this version cannot create a test world; create one named '{}' first", WORLD, e);
            state = State.DONE;
        }
    }

    /** Frame boundary, called at the start of the HUD pass (once per rendered frame). */
    public static void frameStart() {
        if (state != State.RECORD) return;
        long now = System.nanoTime();
        if (lastFrame != 0 && frames < MAX_FRAMES) frameNs[frames++] = now - lastFrame;
        lastFrame = now;
        hudStart = now;
    }

    /** End of Astro's HUD drawing for this frame. */
    public static void hudEnd() {
        if (state != State.RECORD || frames == 0) return;
        hudNs[frames - 1] += System.nanoTime() - hudStart;
    }

    private static void sampleSystem() {
        Runtime rt = Runtime.getRuntime();
        heapPeak = Math.max(heapPeak, rt.totalMemory() - rt.freeMemory());
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            double load = os.getProcessCpuLoad();
            if (load >= 0) {
                cpuSum += load;
                cpuSamples++;
            }
        }
    }

    private static void write(MinecraftClient client, long elapsedNs) {
        long[] f = Arrays.copyOf(frameNs, frames);
        long[] h = Arrays.copyOf(hudNs, frames);
        frameNs = null;
        hudNs = null;
        Arrays.sort(f);
        double total = 0;
        for (long v : f) total += v;
        int worst = Math.max(1, frames / 100);
        double worstSum = 0;
        for (int i = frames - worst; i < frames; i++) worstSum += f[i];
        double hudTotal = 0;
        for (long v : h) hudTotal += v;
        Runtime rt = Runtime.getRuntime();
        System.gc();
        long steady = rt.totalMemory() - rt.freeMemory();
        String json = String.format(Locale.ROOT,
                "{\"name\":\"%s\",\"hud\":\"%s\",\"frames\":%d,\"seconds\":%.2f,\"avgFps\":%.1f,\"low1Fps\":%.1f,"
                        + "\"avgFrameMs\":%.3f,\"p99FrameMs\":%.3f,\"hudMsPerFrame\":%.4f,\"cpuProcessPct\":%.1f,"
                        + "\"heapPeakMb\":%.1f,\"heapSteadyMb\":%.1f}",
                NAME, HUD, frames, elapsedNs / 1e9, frames / (total / 1e9), worst / (worstSum / 1e9),
                total / frames / 1e6, f[Math.min(frames - 1, (int) (frames * 0.99))] / 1e6, hudTotal / frames / 1e6,
                cpuSamples == 0 ? -1 : cpuSum / cpuSamples * 100, heapPeak / 1048576.0, steady / 1048576.0);
        try {
            Path out = client.runDirectory.toPath().resolve("bench").resolve(NAME + ".json");
            Files.createDirectories(out.getParent());
            Files.writeString(out, json);
            AstroClient.LOGGER.info("[bench] {}", json);
        } catch (Exception e) {
            AstroClient.LOGGER.error("[bench] could not write results", e);
        }
    }
}
