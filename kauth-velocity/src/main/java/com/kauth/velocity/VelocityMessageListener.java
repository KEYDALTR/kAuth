package com.kauth.velocity;

import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.UUID;

public class VelocityMessageListener {

    private final KAuthVelocity plugin;

    public VelocityMessageListener(KAuthVelocity plugin) {
        this.plugin = plugin;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(KAuthVelocity.CHANNEL)) return;
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Sadece backend sunucularından gelen mesajları işle
        if (!(event.getSource() instanceof ServerConnection sourceConnection)) return;

        AuthMessage msg = AuthMessage.fromBytes(event.getData());
        UUID uuid = UUID.fromString(msg.playerUuid());

        switch (msg.type()) {
            case MessageConstants.LOGIN -> {
                plugin.setAuthenticated(uuid, true);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " giriş yaptı → diğer sunuculara bildiriliyor");
                broadcastExcept(sourceConnection.getServer(), msg.toBytes());
            }
            case MessageConstants.LOGOUT -> {
                plugin.setAuthenticated(uuid, false);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " çıkış yaptı → diğer sunuculara bildiriliyor");
                broadcastExcept(sourceConnection.getServer(), msg.toBytes());
            }
            case MessageConstants.FORCE_LOGOUT -> {
                plugin.setAuthenticated(uuid, false);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " zorla çıkış → tüm sunuculara bildiriliyor");
                broadcastToAll(msg.toBytes());
            }
        }
    }

    private void broadcastExcept(RegisteredServer excludeServer, byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            if (!server.equals(excludeServer) && !server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
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
