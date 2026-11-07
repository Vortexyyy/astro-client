package dev.astroclient.compat;

import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;

import java.net.UnknownHostException;

/** Version shim (1.21.10). */
final class ServerPing {
    private ServerPing() {}

    static void add(MultiplayerServerListPinger pinger, ServerInfo info, Runnable saver, Runnable done) throws UnknownHostException {
        pinger.add(info, saver, done);
    }
}
