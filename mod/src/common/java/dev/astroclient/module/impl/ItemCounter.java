package dev.astroclient.module.impl;

import dev.astroclient.compat.Gfx;
import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.ModeSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

/**
 * How many of one kind of item you carry (arrows, potions, golden apples, blocks or pearls), with its icon.
 * Flat items are drawn straight from their item texture (one quad, follows resource packs); only Blocks
 * goes through the full item renderer, since block icons are 3D models.
 */
public final class ItemCounter extends HudModule {
    private static final Identifier ARROW_TEX = Identifier.of("minecraft", "textures/item/arrow.png");
    private static final Identifier POTION_TEX = Identifier.of("minecraft", "textures/item/splash_potion.png");
    private static final Identifier POTION_LIQUID_TEX = Identifier.of("minecraft", "textures/item/potion_overlay.png");
    private static final Identifier GAPPLE_TEX = Identifier.of("minecraft", "textures/item/golden_apple.png");
    private static final Identifier PEARL_TEX = Identifier.of("minecraft", "textures/item/ender_pearl.png");
    private static final int PAD = 3;
    private static final int ICON = 16;
    private static final int GAP = 4;

    private final ModeSetting item = add(new ModeSetting("Item", "Arrows", "Arrows", "Potions", "Golden apples", "Blocks", "Ender pearls"));
    private final BooleanSetting hideEmpty = add(new BooleanSetting("Hide when none", false));
    private final BooleanSetting background = add(new BooleanSetting("Background", true));

    private ItemStack block;
    private ItemStack icon = ItemStack.EMPTY;
    private int count = -1;
    private String countText = "0";
    private int textWidth;

    public ItemCounter() {
        super("Item Counter", "Counts arrows, potions, gapples, blocks or pearls");
    }

    private void icons() {
        if (block == null) block = new ItemStack(Items.COBBLESTONE);
    }

    private static boolean matches(String mode, Item it, Item held) {
        return switch (mode) {
            case "Arrows" -> it == Items.ARROW || it == Items.SPECTRAL_ARROW || it == Items.TIPPED_ARROW;
            case "Potions" -> it == Items.POTION || it == Items.SPLASH_POTION || it == Items.LINGERING_POTION;
            case "Golden apples" -> it == Items.GOLDEN_APPLE || it == Items.ENCHANTED_GOLDEN_APPLE;
            case "Ender pearls" -> it == Items.ENDER_PEARL;
            // the held block type, or every block when not holding one
            default -> held != null ? it == held : it instanceof BlockItem;
        };
    }

    /** Recounts at the HUD refresh rate; the string is only rebuilt when the number changes. */
    private void refresh(boolean editing) {
        if (!due(editing)) return;
        icons();
        String mode = item.get();
        int n;
        if (mc.player == null) {
            n = editing ? 64 : 0;
            icon = block;
        } else {
            PlayerInventory inv = mc.player.getInventory();
            ItemStack heldStack = mc.player.getMainHandStack();
            Item held = !heldStack.isEmpty() && heldStack.getItem() instanceof BlockItem ? heldStack.getItem() : null;
            ItemStack first = null;
            n = 0;
            for (int i = 0, size = inv.size(); i < size; i++) {
                ItemStack s = inv.getStack(i);
                if (s.isEmpty() || !matches(mode, s.getItem(), held)) continue;
                n += s.getCount();
                if (first == null) first = s;
            }
            icon = held != null ? heldStack : first != null && mode.equals("Blocks") ? first : block;
        }
        if (n != count) {
            count = n;
            countText = String.valueOf(n);
            textWidth = mc.textRenderer.getWidth(countText);
        }
    }

    @Override
    public boolean visible(boolean editing) {
        refresh(editing);
        return editing || !hideEmpty.get() || count > 0;
    }

    @Override
    public boolean batchable() {
        return false; // draws an item
    }

    @Override
    public int width(boolean editing) {
        refresh(editing);
        return PAD * 2 + ICON + GAP + Math.max(textWidth, 6) + 2;
    }

    @Override
    public int height(boolean editing) {
        return ICON + PAD * 2;
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        refresh(editing);
        int w = width(editing);
        if (background.get()) RenderUtil.panel(context, x, y, w, height(editing), Theme.R_MD, Theme.HUD_BACKGROUND, Theme.BORDER);
        int ix = x + PAD, iy = y + PAD;
        switch (item.get()) {
            case "Arrows" -> Gfx.texture(context, ARROW_TEX, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
            case "Potions" -> {
                Gfx.texture(context, POTION_LIQUID_TEX, ix, iy, 0, 0, ICON, ICON, ICON, ICON, Theme.ACCENT);
                Gfx.texture(context, POTION_TEX, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
            }
            case "Golden apples" -> Gfx.texture(context, GAPPLE_TEX, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
            case "Ender pearls" -> Gfx.texture(context, PEARL_TEX, ix, iy, 0, 0, ICON, ICON, ICON, ICON);
            default -> {
                if (!icon.isEmpty()) context.drawItem(icon, ix, iy);
            }
        }
        context.drawText(mc.textRenderer, countText, x + PAD + ICON + GAP, y + PAD + 4, count > 0 ? Theme.TEXT : Theme.MUTED, !background.get());
    }
}
