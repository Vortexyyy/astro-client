package dev.astroclient.module.impl;

import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.module.HudModule;
import dev.astroclient.module.setting.BooleanSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

/** Worn armor (and optionally the held item) with remaining durability. */
public final class ArmorStatus extends HudModule {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;

    private final BooleanSetting heldItem = add(new BooleanSetting("Held item", true));
    private final BooleanSetting background = add(new BooleanSetting("Background", true));

    private ItemStack[] cachedStacks = new ItemStack[0];
    private String[] labels = new String[0];
    private int[] colours = new int[0];

    public ArmorStatus() {
        super("Armor", "Shows your armor and its durability");
    }

    private ItemStack[] stacks(boolean editing) {
        int n = heldItem.get() ? 5 : 4;
        ItemStack[] out = new ItemStack[n];
        if (mc.player == null) {
            ItemStack[] sample = {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.IRON_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS), new ItemStack(Items.DIAMOND_SWORD)};
            System.arraycopy(sample, 0, out, 0, n);
            return out;
        }
        for (int i = 0; i < 4; i++) out[i] = mc.player.getEquippedStack(ARMOR[i]);
        if (n == 5) out[4] = mc.player.getMainHandStack();
        return out;
    }

    /** Equipment and durability labels, rebuilt at the HUD refresh rate instead of several times a frame. */
    private ItemStack[] current(boolean editing) {
        if (due(editing)) {
            cachedStacks = stacks(editing);
            labels = new String[cachedStacks.length];
            colours = new int[cachedStacks.length];
            for (int i = 0; i < cachedStacks.length; i++) {
                ItemStack stack = cachedStacks[i];
                if (stack == null || stack.isEmpty()) continue;
                if (stack.isDamageable()) {
                    float left = (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                    colours[i] = left > 0.5f ? Theme.GOOD : left > 0.2f ? Theme.WARN : Theme.BAD;
                    labels[i] = Math.round(left * 100) + "%";
                } else if (stack.getCount() > 1) {
                    colours[i] = Theme.TEXT;
                    labels[i] = "x" + stack.getCount();
                }
            }
        }
        return cachedStacks;
    }

    @Override
    public boolean visible(boolean editing) {
        if (editing) return true;
        for (ItemStack s : current(false)) if (s != null && !s.isEmpty()) return true;
        return false;
    }

    @Override
    public boolean batchable() {
        return false;
    }

    @Override
    public int width(boolean editing) {
        return 16 + 6 + mc.textRenderer.getWidth("100%") + (background.get() ? 8 : 0);
    }

    @Override
    public int height(boolean editing) {
        return current(editing).length * ROW - 2 + (background.get() ? 6 : 0);
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        ItemStack[] stacks = current(editing);
        int pad = background.get() ? 4 : 0;
        if (background.get()) {
            RenderUtil.panel(context, x, y, width(editing), height(editing), Theme.R_MD, Theme.HUD_BACKGROUND, Theme.BORDER);
        }
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;
            int rowY = y + pad - 1 + i * ROW;
            context.drawItem(stack, x + pad, rowY);
            if (labels[i] != null) context.drawText(mc.textRenderer, labels[i], x + pad + 20, rowY + 4, colours[i], true);
        }
    }
}
