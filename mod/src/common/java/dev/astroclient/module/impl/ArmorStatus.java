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

    @Override
    public boolean visible(boolean editing) {
        if (editing) return true;
        for (ItemStack s : stacks(false)) if (s != null && !s.isEmpty()) return true;
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
        return stacks(editing).length * ROW - 2 + (background.get() ? 6 : 0);
    }

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        ItemStack[] stacks = stacks(editing);
        int pad = background.get() ? 4 : 0;
        if (background.get()) {
            RenderUtil.roundedRect(context, x, y, width(editing), height(editing), 5, Theme.HUD_BACKGROUND);
        }
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack == null || stack.isEmpty()) continue;
            int rowY = y + pad - 1 + i * ROW;
            context.drawItem(stack, x + pad, rowY);
            if (stack.isDamageable()) {
                float left = (stack.getMaxDamage() - stack.getDamage()) / (float) stack.getMaxDamage();
                int colour = left > 0.5f ? 0xFF34D399 : left > 0.2f ? 0xFFFACC15 : 0xFFF43F5E;
                context.drawText(mc.textRenderer, Math.round(left * 100) + "%", x + pad + 20, rowY + 4, colour, true);
            } else if (stack.getCount() > 1) {
                context.drawText(mc.textRenderer, "x" + stack.getCount(), x + pad + 20, rowY + 4, Theme.TEXT, true);
            }
        }
    }
}
