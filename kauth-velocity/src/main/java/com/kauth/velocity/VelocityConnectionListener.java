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
     *
     * LimboFilter uyumluluğu:
     * - Limbo/filtre sunucularına LOGIN gönderilmez
     * - previousServer yoksa (ilk bağlantı) → oyuncu LimboFilter'dan geliyor olabilir,
     *   sadece authenticate ise gönder
     * - previousServer limbo ise → LimboFilter'ı geçti, backend'e düşüyor
     */
    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getUsername();
        RegisteredServer newServer = event.getServer();
        String newServerName = newServer.getServerInfo().getName().toLowerCase();

        // Limbo/filtre sunucularına mesaj gönderme
        if (plugin.isIgnoredServer(newServerName)) {
            plugin.getLogger().info("[ServerSwitch] " + name + " → " + newServerName + " (limbo/filtre, atlandı)");
            return;
        }

        if (!plugin.isAuthenticated(uuid)) {
            return;
        }

        AuthMessage msg = new AuthMessage(MessageConstants.LOGIN, uuid.toString(), name);

        // Gecikme ile gönder - kanal hazırlığı ve LimboFilter sonrası stabilite için
        plugin.getServer().getScheduler()
                .buildTask(plugin, () -> {
                    // Oyuncu hala aynı sunucuda ve online mı kontrol et
                    event.getPlayer().getCurrentServer().ifPresent(currentServer -> {
                        if (currentServer.getServerInfo().getName().equals(newServer.getServerInfo().getName())) {
                            newServer.sendPluginMessage(KAuthVelocity.CHANNEL, msg.toBytes());
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
            // Limbo sunucularına broadcast gönderme
            if (plugin.isIgnoredServer(serverName)) continue;
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
        }
    }
}
