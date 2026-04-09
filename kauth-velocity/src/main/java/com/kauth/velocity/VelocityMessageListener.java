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

        if (!(event.getSource() instanceof ServerConnection sourceConnection)) return;

        byte[] secret = plugin.getSecret();
        if (secret.length == 0) {
            plugin.getLogger().warn("[Güvenlik] Plugin mesajı alındı ama secret ayarlı değil, reddediliyor");
            return;
        }

        final AuthMessage msg;
        try {
            msg = AuthMessage.fromBytes(event.getData(), secret);
        } catch (AuthMessage.MessageVerificationException e) {
            plugin.getLogger().warn("[Güvenlik] Geçersiz plugin mesajı " + sourceConnection.getServerInfo().getName()
                    + "'den: " + e.getMessage());
            return;
        } catch (Exception e) {
            plugin.getLogger().warn("[Güvenlik] Mesaj parse hatası: " + e.getMessage());
            return;
        }

        try {
            UUID.fromString(msg.playerUuid());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warn("[Güvenlik] Geçersiz UUID: " + msg.playerUuid());
            return;
        }

        UUID uuid = UUID.fromString(msg.playerUuid());

        AuthMessage fresh = new AuthMessage(msg.type(), msg.playerUuid(), msg.playerName());
        byte[] signed = fresh.toBytes(secret);

        switch (msg.type()) {
            case MessageConstants.LOGIN -> {
                plugin.setAuthenticated(uuid, true);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " giriş yaptı → diğer sunuculara bildiriliyor");
                broadcastExcept(sourceConnection.getServer(), signed);
            }
            case MessageConstants.LOGOUT -> {
                plugin.setAuthenticated(uuid, false);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " çıkış yaptı → diğer sunuculara bildiriliyor");
                broadcastExcept(sourceConnection.getServer(), signed);
            }
            case MessageConstants.FORCE_LOGOUT -> {
                plugin.setAuthenticated(uuid, false);
                plugin.getLogger().info("[Sync] " + msg.playerName() + " zorla çıkış → tüm sunuculara bildiriliyor");
                broadcastToAll(signed);
            }
            default -> plugin.getLogger().warn("[Sync] Bilinmeyen mesaj tipi: " + msg.type());
        }
    }

    private void broadcastExcept(RegisteredServer excludeServer, byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName().toLowerCase();
            if (plugin.isIgnoredServer(name)) continue;
            if (!server.equals(excludeServer) && !server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
        }
    }

    private void broadcastToAll(byte[] data) {
        for (RegisteredServer server : plugin.getServer().getAllServers()) {
            String name = server.getServerInfo().getName().toLowerCase();
            if (plugin.isIgnoredServer(name)) continue;
            if (!server.getPlayersConnected().isEmpty()) {
                server.sendPluginMessage(KAuthVelocity.CHANNEL, data);
            }
        }
    }
}
