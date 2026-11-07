package dev.astroclient.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.network.NetworkingBackend;

import java.net.UnknownHostException;

/** Version shim (1.21.11): pings take the networking backend explicitly. */
final class ServerPing {
    private ServerPing() {}

    static void add(MultiplayerServerListPinger pinger, ServerInfo info, Runnable saver, Runnable done) throws UnknownHostException {
        boolean nativeTransport = MinecraftClient.getInstance().options.shouldUseNativeTransport();
        pinger.add(info, saver, done, NetworkingBackend.remote(nativeTransport));
    }
}
