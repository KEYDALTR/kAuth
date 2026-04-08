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

    /**
     * Oyuncu tamamen proxy'den ayrıldığında tüm backend'lere LOGOUT gönder.
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();

        if (plugin.isAuthenticated(uuid)) {
            plugin.setAuthenticated(uuid, false);
            AuthMessage msg = new AuthMessage(MessageConstants.LOGOUT, uuid.toString(), name);
            broadcastToAll(msg.toBytes());
            plugin.getLogger().info("[Disconnect] " + name + " proxy'den ayrıldı → tüm sunuculara LOGOUT gönderildi");
        }
    }

    /**
     * Oyuncu sunucu değiştirdiğinde, eğer authenticate ise yeni sunucuya LOGIN gönder.
     * Kısa gecikme eklenir çünkü kanal hemen hazır olmayabilir.
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();

        if (plugin.isAuthenticated(uuid)) {
            RegisteredServer newServer = event.getServer();
            AuthMessage msg = new AuthMessage(MessageConstants.LOGIN, uuid.toString(), name);

            // 500ms gecikme ile gönder (kanal hazırlığı için)
            plugin.getServer().getScheduler()
                    .buildTask(plugin, () -> {
                        newServer.sendPluginMessage(KAuthVelocity.CHANNEL, msg.toBytes());
                        plugin.getLogger().info("[ServerSwitch] " + name + " → " + newServer.getServerInfo().getName() + " (LOGIN gönderildi)");
                    })
                    .delay(500, TimeUnit.MILLISECONDS)
                    .schedule();
        }
    }

    private void broadcastToAll(byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
        }
    }
}
