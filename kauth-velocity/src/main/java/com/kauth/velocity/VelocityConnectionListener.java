package com.kauth.velocity;

import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class VelocityConnectionListener {

    private final KAuthVelocity plugin;

    public VelocityConnectionListener(KAuthVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();

        if (plugin.isAuthenticated(uuid)) {
            plugin.setAuthenticated(uuid, false);
            byte[] secret = plugin.getSecret();
            if (secret.length == 0) return;
            AuthMessage msg = new AuthMessage(MessageConstants.LOGOUT, uuid.toString(), name);
            broadcastToAll(msg.toBytes(secret));
            plugin.getLogger().info("[Disconnect] " + name + " proxy'den ayrıldı → tüm sunuculara LOGOUT gönderildi");
        }
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();
        RegisteredServer newServer = event.getServer();
        String newServerName = newServer.getServerInfo().getName().toLowerCase();

        if (plugin.isIgnoredServer(newServerName)) {
            plugin.getLogger().info("[ServerSwitch] " + name + " → " + newServerName + " (limbo/filtre, atlandı)");
            return;
        }

        if (!plugin.isAuthenticated(uuid)) {
            return;
        }

        byte[] secret = plugin.getSecret();
        if (secret.length == 0) return;

        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    event.getPlayer().getCurrentServer().ifPresent(currentServer -> {
                        if (currentServer.getServerInfo().getName().equals(newServer.getServerInfo().getName())) {
                            AuthMessage msg = new AuthMessage(MessageConstants.LOGIN, uuid.toString(), name);
                            newServer.sendPluginMessage(KAuthVelocity.CHANNEL, msg.toBytes(secret));
                            plugin.getLogger().info("[ServerSwitch] " + name + " → " + newServer.getServerInfo().getName() + " (LOGIN gönderildi)");
                        }
                    });
                })
                .delay(1, TimeUnit.SECONDS)
                .schedule();
    }

    private void broadcastToAll(byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String serverName = server.getServerInfo().getName().toLowerCase();
            if (plugin.isIgnoredServer(serverName)) continue;
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
        }
    }
}
