package dev.astroclient.module.impl;

import dev.astroclient.gui.render.RenderUtil;
import dev.astroclient.gui.render.Theme;
import dev.astroclient.gui.theme.ThemeSettings;
import dev.astroclient.module.TextHudModule;
import dev.astroclient.module.setting.BooleanSetting;
import dev.astroclient.module.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Util;

/**
 * Combo counter: consecutive hits landed on one target before you get hit or a pause without hitting.
 * Attacks come from ClientPlayerInteractionManagerMixin; a hit counts once the target's hurt animation
 * restarts (the server accepted it), and the damage is the target's synced health drop. Purely cosmetic.
 */
public final class ComboDisplay extends TextHudModule {
    private static ComboDisplay instance;
    /** Ticks after an attack in which the target's hurt animation must start for it to count. */
    private static final int CONFIRM_TICKS = 10;
    /** Ticks after a hit in which the target's health drop is read as that hit's damage. */
    private static final int DAMAGE_TICKS = 10;
    private static final long FLASH_MS = 220;

    private final BooleanSetting showDamage = add(new BooleanSetting("Show damage", true));
    private final BooleanSetting hitFlash = add(new BooleanSetting("Hit flash", true));
    private final NumberSetting resetAfter = add(new NumberSetting("Reset after (s)", 2, 1, 5, 0.5));

    private LivingEntity target;
    private int lastTargetHurt;
    private int pendingTicks;
    private float healthAtHit;
    private int damageTicks;
    private int lastPlayerHurt;

    private int combo;
    private float lastDamage;
    private long lastHitMs;
    private long flashStart = -FLASH_MS;

    public ComboDisplay() {
        super("Combo", "Counts your hit combo and last hit damage");
        instance = this;
    }

    /** Called by the attack mixin on the client thread; cheap no-op while disabled. */
    public static void onAttack(Entity entity) {
        ComboDisplay m = instance;
        if (m == null || !m.isEnabled() || !(entity instanceof LivingEntity living)) return;
        if (living != m.target) {
            m.target = living;
            m.combo = 0;
            m.damageTicks = 0;
        }
        m.lastTargetHurt = living.hurtTime;
        m.pendingTicks = CONFIRM_TICKS;
        m.healthAtHit = living.getHealth() + living.getAbsorptionAmount();
    }

    @Override
    protected void onDisable() {
        target = null;
        combo = 0;
        lastDamage = 0;
        pendingTicks = 0;
        damageTicks = 0;
    }

    @Override
    public void onTick() {
        long now = Util.getMeasuringTimeMs();
        // you got hit: combo over
        int hurt = mc.player.hurtTime;
        if (hurt > lastPlayerHurt) combo = 0;
        lastPlayerHurt = hurt;

        LivingEntity t = target;
        if (t != null && t.isRemoved() && pendingTicks == 0 && damageTicks == 0) target = t = null;
        if (t != null) {
            float health = t.getHealth() + t.getAbsorptionAmount();
            if (pendingTicks > 0) {
                pendingTicks--;
                // hurtTime only ever counts down, so any rise is a fresh hurt animation
                if (t.hurtTime > lastTargetHurt) {
                    pendingTicks = 0;
                    combo++;
                    lastHitMs = now;
                    flashStart = now;
                    lastDamage = 0;
                    damageTicks = DAMAGE_TICKS;
                }
            }
            if (damageTicks > 0) {
                damageTicks--;
                float dealt = healthAtHit - health;
                if (dealt > lastDamage) lastDamage = dealt;
            }
            lastTargetHurt = t.hurtTime;
        }
        if (combo > 0 && now - lastHitMs > (long) (resetAfter.get() * 1000)) combo = 0;
    }

    @Override
    protected String label() {
        return "COMBO";
    }

    @Override
    protected String text(boolean editing) {
        if (mc.player == null) return editing ? "4  ·  3.5❤" : null;
        // damage in hearts (health / 2), one decimal; 0 hides it
        int tenths = showDamage.get() ? Math.round(lastDamage * 5f) : 0;
        if (combo == shownCombo && tenths == shownTenths && shown != null) return shown;
        shownCombo = combo;
        shownTenths = tenths;
        shown = tenths <= 0 ? String.valueOf(combo) : combo + "  ·  " + tenths / 10 + "." + tenths % 10 + "❤";
        return shown;
    }

    private String shown;
    private int shownCombo = -1;
    private int shownTenths = -1;

    @Override
    public void render(DrawContext context, int x, int y, boolean editing) {
        super.render(context, x, y, editing);
        if (!hitFlash.get() || ThemeSettings.get().effects == ThemeSettings.Quality.OFF) return;
        long age = Util.getMeasuringTimeMs() - flashStart;
        if (age < 0 || age >= FLASH_MS) return;
        float a = 1f - age / (float) FLASH_MS;
        float peak = ThemeSettings.get().effects == ThemeSettings.Quality.LOW ? 0.18f : 0.32f;
        RenderUtil.roundedRect(context, x, y, width(editing), height(editing), Theme.R_MD, Theme.withAlpha(Theme.ACCENT, a * a * peak));
    }
}
