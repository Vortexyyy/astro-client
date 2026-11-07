package dev.astroclient.gui.hud;

import dev.astroclient.AstroClient;
import dev.astroclient.compat.AstroScreen;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.render.Ui;
import dev.astroclient.gui.title.AstroButton;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.ModuleManager.Placed;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Drag-to-move HUD layout. Every enabled HUD element is shown (with sample values where needed);
 * elements snap to the screen edges, the centre lines and each other. Right-click puts one back in
 * the automatic top-left stack.
 */
public class HudEditorScreen extends AstroScreen {
    private static final int SNAP = 4;
    private static final int GUIDE = 0x9922D3EE;

    private final Screen parent;
    private HudModule dragging;
    private double grabX;
    private double grabY;
    private int guideX = -1;
    private int guideY = -1;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("Edit HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 80;
        addDrawableChild(new AstroButton(width / 2 - w - 3, 20, w, 20, Text.literal("Reset all"), b -> {
            for (var m : AstroClient.get().modules().all()) if (m instanceof HudModule h) h.resetPosition();
        }));
        addDrawableChild(new AstroButton(width / 2 + 3, 20, w, 20, Text.literal("Done"), b -> close()));
    }

    private List<Placed> placed() {
        return AstroClient.get().modules().layout(width, height, true);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // light tint so the world stays visible while arranging
        context.fill(0, 0, width, height, client.world == null ? 0xFF0B0A24 : 0x55000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        // centre lines (brighter while snapped to them)
        context.fill(width / 2, 0, width / 2 + 1, height, guideX == width / 2 ? GUIDE : 0x22FFFFFF);
        context.fill(0, height / 2, width, height / 2 + 1, guideY == height / 2 ? GUIDE : 0x22FFFFFF);
        if (guideX >= 0 && guideX != width / 2) context.fill(guideX, 0, guideX + 1, height, GUIDE);
        if (guideY >= 0 && guideY != height / 2) context.fill(0, guideY, width, guideY + 1, GUIDE);

        Placed hovered = null;
        for (Placed p : placed()) {
            p.module().render(context, p.x(), p.y(), true);
            boolean over = p.module() == dragging || (dragging == null && Ui.inside(mouseX, mouseY, p.x(), p.y(), p.w(), p.h()));
            if (over) hovered = p;
            outline(context, p, over);
        }

        if (hovered != null) {
            String label = hovered.module().getName() + (hovered.module().hasPosition() ? "" : " (auto)");
            int lw = textRenderer.getWidth(label) + 8;
            int ly = hovered.y() > 14 ? hovered.y() - 13 : hovered.y() + hovered.h() + 2;
            RenderUtil.roundedRect(context, hovered.x(), ly, lw, 11, 3, Theme.ACCENT);
            context.drawText(textRenderer, label, hovered.x() + 4, ly + 2, 0xFFFFFFFF, false);
        }

        String hint = "Drag to move  ·  Right-click to reset  ·  Esc when done";
        context.drawCenteredTextWithShadow(textRenderer, hint, width / 2, 7, Theme.MUTED);
        if (placed().isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No HUD modules are enabled. Turn some on in the mod menu.", width / 2, height / 2 - 20, Theme.TEXT);
        }
    }

    private void outline(DrawContext c, Placed p, boolean active) {
        int col = active ? Theme.ACCENT_2 : 0x66FFFFFF;
        int x1 = p.x() - 1;
        int y1 = p.y() - 1;
        int x2 = p.x() + p.w() + 1;
        int y2 = p.y() + p.h() + 1;
        c.fill(x1, y1, x2, y1 + 1, col);
        c.fill(x1, y2 - 1, x2, y2, col);
        c.fill(x1, y1, x1 + 1, y2, col);
        c.fill(x2 - 1, y1, x2, y2, col);
    }

    @Override
    protected boolean onMouseClicked(double mouseX, double mouseY, int button) {
        List<Placed> all = placed();
        for (int i = all.size() - 1; i >= 0; i--) {
            Placed p = all.get(i);
            if (!Ui.inside(mouseX, mouseY, p.x(), p.y(), p.w(), p.h())) continue;
            if (button == 1) {
                p.module().resetPosition();
            } else if (button == 0) {
                dragging = p.module();
                grabX = mouseX - p.x();
                grabY = mouseY - p.y();
                // fix an auto-stacked element in place so it starts moving from where it is
                p.module().setPixelPosition(p.x(), p.y(), p.w(), p.h(), width, height);
            }
            return true;
        }
        return false;
    }

    @Override
    protected boolean onMouseDragged(double mouseX, double mouseY, int button) {
        if (dragging == null) return false;
        int w = dragging.width(true);
        int h = dragging.height(true);
        int x = (int) Math.round(mouseX - grabX);
        int y = (int) Math.round(mouseY - grabY);
        guideX = -1;
        guideY = -1;

        // snap: screen edges, centre lines, then other elements' edges
        int[] sx = snap(x, w, width, true);
        int[] sy = snap(y, h, height, false);
        x = Math.max(0, Math.min(width - w, sx[0]));
        y = Math.max(0, Math.min(height - h, sy[0]));
        guideX = sx[1];
        guideY = sy[1];
        dragging.setPixelPosition(x, y, w, h, width, height);
        return true;
    }

    /** Returns {snapped position, guide line or -1}. */
    private int[] snap(int pos, int size, int screen, boolean horizontal) {
        int[][] targets = {{0, -1}, {screen - size, -1}, {screen / 2 - size / 2, screen / 2}};
        for (int[] t : targets) if (Math.abs(pos - t[0]) <= SNAP) return new int[] {t[0], t[1]};
        for (Placed p : placed()) {
            if (p.module() == dragging) continue;
            int start = horizontal ? p.x() : p.y();
            int end = start + (horizontal ? p.w() : p.h());
            int[][] edges = {{start, start}, {end - size, end}, {end + 2, end}, {start - size - 2, start}};
            for (int[] e : edges) if (Math.abs(pos - e[0]) <= SNAP) return new int[] {e[0], e[1]};
        }
        return new int[] {pos, -1};
    }

    @Override
    protected boolean onMouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        guideX = -1;
        guideY = -1;
        return false;
    }

    @Override
    protected boolean onKeyPressed(int key, int scancode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        AstroClient.get().saveConfig();
        client.setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
