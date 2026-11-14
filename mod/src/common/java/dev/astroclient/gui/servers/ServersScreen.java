package dev.astroclient.gui.servers;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.compat.Gfx;
import dev.astroclient.compat.Platform;
import dev.astroclient.gui.render.Animation;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.render.Ui;
import dev.astroclient.gui.theme.Backgrounds;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.gui.title.AstroButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.screen.multiplayer.DirectConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.io.ByteArrayInputStream;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Astro's Multiplayer menu: server cards with favicon, MOTD, live ping and player count.
 * Uses the same servers.dat as vanilla, so servers added here show up everywhere.
 */
public class ServersScreen extends AstroScreen {
    private static final int MAX_W = 560;
    private static final int HEADER = 40;
    private static final int CARD_H = 46;
    private static final int GAP = 6;
    private static final int PAD = 12;
    private static final int ICON = 32;

    private final Screen parent;
    private final Animation scroll = new Animation(0f, 18f);
    private final Map<ServerInfo, Animation> hovers = new HashMap<>();
    private final Map<ServerInfo, Identifier> favicons = new HashMap<>();
    private final Map<ServerInfo, byte[]> faviconBytes = new HashMap<>();
    private final List<ServerInfo> servers = new ArrayList<>();
    private ServerList serverList;
    private MultiplayerServerListPinger pinger;
    private ExecutorService pingThreads;
    private int px;
    private int py;
    private int pw;
    private int ph;

    public ServersScreen(Screen parent) {
        super(Text.translatable("multiplayer.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        pw = Math.min(width - 32, MAX_W);
        ph = height - 48;
        px = (width - pw) / 2;
        py = 24;

        int bx = px + pw - PAD;
        bx = button(bx, "Back", 50, this::close);
        bx = button(bx, "Refresh", 60, this::refresh);
        bx = button(bx, "Direct connect", 96, this::directConnect);
        button(bx, "Add server", 74, this::addServer);

        // first open, or coming back from add/edit/connect screens (removed() stops pinging)
        if (pinger == null) refresh();
    }

    private int button(int right, String label, int w, Runnable action) {
        addDrawableChild(new AstroButton(right - w, py + PAD, w, 20, Text.literal(label), b -> action.run()));
        return right - w - 6;
    }

    /** Reloads servers.dat and pings every server again. */
    private void refresh() {
        stopPinging();
        serverList = new ServerList(client);
        serverList.loadFile();
        servers.clear();
        for (int i = 0; i < serverList.size(); i++) servers.add(serverList.get(i));
        pinger = new MultiplayerServerListPinger();
        pingThreads = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "Astro server ping");
            t.setDaemon(true);
            return t;
        });
        for (ServerInfo info : servers) ping(info);
    }

    private void ping(ServerInfo info) {
        info.setStatus(ServerInfo.Status.PINGING);
        info.label = Text.translatable("multiplayer.status.pinging").formatted(Formatting.DARK_GRAY);
        info.playerCountLabel = Text.empty();
        MultiplayerServerListPinger current = pinger;
        pingThreads.submit(() -> {
            try {
                Platform.pingServer(current, info, () -> client.execute(() -> serverList.saveFile()), () -> {});
            } catch (UnknownHostException e) {
                info.setStatus(ServerInfo.Status.UNREACHABLE);
                info.label = Text.translatable("multiplayer.status.cannot_resolve").formatted(Formatting.RED);
            } catch (Exception e) {
                info.setStatus(ServerInfo.Status.UNREACHABLE);
                info.label = Text.translatable("multiplayer.status.cannot_connect").formatted(Formatting.RED);
            }
        });
    }

    private void stopPinging() {
        if (pinger != null) pinger.cancel();
        if (pingThreads != null) pingThreads.shutdownNow();
        pinger = null;
        pingThreads = null;
    }

    @Override
    public void tick() {
        if (pinger != null) pinger.tick();
    }

    // ── Actions ─────────────────────────────────────────────────────────

    private void join(ServerInfo info) {
        stopPinging();
        ConnectScreen.connect(this, client, ServerAddress.parse(info.address), info, false, null);
    }

    private void addServer() {
        ServerInfo info = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
        client.setScreen(Platform.addServerScreen(this, ok -> {
            if (ok) {
                serverList.add(info, false);
                serverList.saveFile();
            }
            serverList = null;
            client.setScreen(this);
        }, info, false));
    }

    private void edit(ServerInfo info) {
        ServerInfo copy = new ServerInfo(info.name, info.address, ServerInfo.ServerType.OTHER);
        copy.copyWithSettingsFrom(info);
        client.setScreen(Platform.addServerScreen(this, ok -> {
            if (ok) {
                info.copyWithSettingsFrom(copy);
                serverList.saveFile();
            }
            serverList = null;
            client.setScreen(this);
        }, copy, true));
    }

    private void delete(ServerInfo info) {
        client.setScreen(new ConfirmScreen(ok -> {
            if (ok) {
                serverList.remove(info);
                serverList.saveFile();
            }
            serverList = null;
            client.setScreen(this);
        }, Text.translatable("selectServer.deleteQuestion"),
                Text.translatable("selectServer.deleteWarning", info.name),
                Text.translatable("selectServer.deleteButton"), Text.translatable("gui.cancel")));
    }

    private void directConnect() {
        ServerInfo info = new ServerInfo(I18n.translate("selectServer.defaultName"), "", ServerInfo.ServerType.OTHER);
        client.setScreen(new DirectConnectScreen(this, ok -> {
            if (ok) join(info);
            else client.setScreen(this);
        }, info));
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private int listTop() {
        return py + HEADER + 8;
    }

    private int listHeight() {
        return py + ph - PAD - listTop();
    }

    private float maxScroll() {
        return Math.max(0, servers.size() * (CARD_H + GAP) - GAP - listHeight());
    }

    /** Uploads (or refreshes) a server's favicon when the ping brings a new one. */
    private Identifier favicon(ServerInfo info) {
        byte[] bytes = info.getFavicon();
        if (bytes == null) return null;
        if (Arrays.equals(bytes, faviconBytes.get(info))) return favicons.get(info);
        faviconBytes.put(info, bytes);
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(bytes));
            Identifier id = Identifier.of("astroclient", "server/" + Integer.toHexString(System.identityHashCode(info)));
            Platform.registerTexture(id, image);
            favicons.put(info, id);
            return id;
        } catch (Exception e) {
            AstroClient.LOGGER.debug("Bad favicon for {}", info.address, e);
            return null;
        }
    }

    private Rect editRect(int x, int y, int w) {
        return new Rect(x + w - 64, y + CARD_H - 16, 26, 12);
    }

    private Rect deleteRect(int x, int y, int w) {
        return new Rect(x + w - 34, y + CARD_H - 16, 28, 12);
    }

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return Ui.inside(mx, my, x, y, w, h);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        Ui.divider(context, px + PAD, py + HEADER, px + pw - PAD);

        scroll.setTarget(MathHelper.clamp(scroll.getTarget(), 0f, maxScroll()));
        float offset = scroll.update();
        int top = listTop();
        int h = listHeight();
        if (servers.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No servers yet. Click Add server or Direct connect.", px + pw / 2, top + 30, Theme.MUTED);
            return;
        }

        boolean inList = Ui.inside(mouseX, mouseY, px, top, pw, h);
        context.enableScissor(px + 2, top, px + pw - 2, top + h);
        for (int i = 0; i < servers.size(); i++) {
            int y = top + Math.round(i * (CARD_H + GAP) - offset);
            if (y + CARD_H < top || y > top + h) continue;
            renderCard(context, servers.get(i), px + PAD, y, pw - PAD * 2, mouseX, mouseY, inList);
        }
        context.disableScissor();
    }

    private void renderCard(DrawContext context, ServerInfo info, int x, int y, int w, int mouseX, int mouseY, boolean inList) {
        boolean hovered = inList && Ui.inside(mouseX, mouseY, x, y, w, CARD_H);
        Animation hover = hovers.computeIfAbsent(info, s -> new Animation(0f, 16f));
        hover.setTarget(hovered ? 1f : 0f);
        float hv = hover.update();

        if (hv > 0.01f) RenderUtil.glow(context, x, y, w, CARD_H, Theme.R_LG, Theme.withAlpha(Theme.ACCENT, 0.35f * hv), 5);
        RenderUtil.roundedRect(context, x, y, w, CARD_H, Theme.R_LG, Theme.lerp(Ui.CARD, Ui.CARD_HOVER, hv));

        int iconX = x + 7;
        int iconY = y + (CARD_H - ICON) / 2;
        Identifier icon = favicon(info);
        if (icon != null) {
            Gfx.push(context);
            Gfx.translate(context, iconX, iconY);
            Gfx.scale(context, ICON / 64f, ICON / 64f);
            Gfx.texture(context, icon, 0, 0, 0, 0, 64, 64, 64, 64);
            Gfx.pop(context);
        } else {
            Ui.initialTile(context, textRenderer, info.name, iconX, iconY, ICON);
        }

        int tx = iconX + ICON + 10;
        int textW = w - ICON - 110;
        context.drawText(textRenderer, textRenderer.trimToWidth(info.name, textW), tx, y + 7, 0xFFFFFFFF, false);
        if (info.label != null) {
            List<OrderedText> motd = textRenderer.wrapLines(info.label, textW);
            for (int i = 0; i < Math.min(2, motd.size()); i++) {
                context.drawText(textRenderer, motd.get(i), tx, y + 19 + i * 10, Theme.MUTED, false);
            }
        }

        // ping + players on the right
        String ping;
        int pingColor;
        ServerInfo.Status status = info.getStatus();
        if (status == ServerInfo.Status.SUCCESSFUL) {
            ping = info.ping + " ms";
            pingColor = info.ping < 150 ? Theme.GOOD : info.ping < 300 ? Theme.WARN : Theme.BAD;
        } else if (status == ServerInfo.Status.PINGING || status == ServerInfo.Status.INITIAL) {
            ping = "…";
            pingColor = Theme.MUTED;
        } else if (status == ServerInfo.Status.INCOMPATIBLE) {
            ping = "Incompatible";
            pingColor = Theme.WARN;
        } else {
            ping = "Offline";
            pingColor = Theme.BAD;
        }
        context.drawText(textRenderer, ping, x + w - 10 - textRenderer.getWidth(ping), y + 7, pingColor, false);
        if (info.playerCountLabel != null && status == ServerInfo.Status.SUCCESSFUL) {
            int pw2 = textRenderer.getWidth(info.playerCountLabel);
            context.drawText(textRenderer, info.playerCountLabel.asOrderedText(), x + w - 10 - pw2, y + 19, Theme.TEXT, false);
        }

        if (hv > 0.05f) {
            Rect edit = editRect(x, y, w);
            Rect del = deleteRect(x, y, w);
            int editColor = edit.contains(mouseX, mouseY) ? Theme.ACCENT_2 : Theme.MUTED;
            int delColor = del.contains(mouseX, mouseY) ? Theme.BAD : Theme.MUTED;
            context.drawText(textRenderer, "Edit", edit.x, edit.y + 2, Theme.withAlpha(editColor, hv), false);
            context.drawText(textRenderer, "Delete", del.x, del.y + 2, Theme.withAlpha(delColor, hv), false);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        ThemeSettings theme = ThemeSettings.get();
        Backgrounds.draw(context, theme.resolve(theme.serversBackground), width, height);
        Ui.panel(context, px, py, pw, ph);
        context.drawText(textRenderer, "Multiplayer", px + PAD + 4, py + PAD + 6, 0xFFFFFFFF, false);
    }

    // ── Input ───────────────────────────────────────────────────────────

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int top = listTop();
        if (button != 0 || !Ui.inside(mouseX, mouseY, px, top, pw, listHeight())) return false;
        int index = (int) ((mouseY - top + scroll.get()) / (CARD_H + GAP));
        if (index < 0 || index >= servers.size()) return false;
        ServerInfo info = servers.get(index);
        int x = px + PAD;
        int y = top + Math.round(index * (CARD_H + GAP) - scroll.get());
        int w = pw - PAD * 2;
        if (editRect(x, y, w).contains(mouseX, mouseY)) edit(info);
        else if (deleteRect(x, y, w).contains(mouseX, mouseY)) delete(info);
        else join(info);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll.setTarget(MathHelper.clamp(scroll.getTarget() - (float) verticalAmount * 30f, 0f, maxScroll()));
        return true;
    }

    @Override
    protected boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5) {
            refresh();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        stopPinging();
        for (Identifier id : favicons.values()) client.getTextureManager().destroyTexture(id);
        favicons.clear();
        faviconBytes.clear();
    }
}
