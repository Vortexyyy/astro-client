package dev.astroclient.module.impl;

import dev.astroclient.module.TextHudModule;
import dev.astroclient.module.setting.BooleanSetting;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Clicks per second, sampled every frame from the attack/use keys (no input hooks needed, so it works
 * the same on every Minecraft version). At high frame rates no click is missed.
 */
public final class CpsCounter extends TextHudModule {
    private final BooleanSetting rightClick = add(new BooleanSetting("Right click", true));
    private final Deque<Long> left = new ArrayDeque<>();
    private final Deque<Long> right = new ArrayDeque<>();
    private boolean wasLeft;
    private boolean wasRight;

    public CpsCounter() {
        super("CPS", "Shows your clicks per second");
    }

    /** Called once per frame by the module manager. */
    public void sample() {
        long now = System.currentTimeMillis();
        boolean l = mc.currentScreen == null && mc.options.attackKey.isPressed();
        boolean r = mc.currentScreen == null && mc.options.useKey.isPressed();
        if (l && !wasLeft) left.addLast(now);
        if (r && !wasRight) right.addLast(now);
        wasLeft = l;
        wasRight = r;
        while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
        while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
    }

    @Override
    protected String label() {
        return "CPS";
    }

    @Override
    protected String text(boolean editing) {
        return rightClick.get() ? left.size() + " / " + right.size() : String.valueOf(left.size());
    }
}
