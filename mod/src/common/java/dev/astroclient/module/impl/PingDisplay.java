package dev.astroclient.module.impl;

import dev.astroclient.module.TextHudModule;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;

public final class PingDisplay extends TextHudModule {
    public PingDisplay() {
        super("Ping", "Shows your latency to the server");
    }

    @Override
    protected String label() {
        return "PING";
    }

    @Override
    protected String text(boolean editing) {
        ClientPlayNetworkHandler handler = mc.getNetworkHandler();
        PlayerListEntry entry = handler == null || mc.player == null ? null : handler.getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return editing ? "42 ms" : null;
        return entry.getLatency() + " ms";
    }
}
