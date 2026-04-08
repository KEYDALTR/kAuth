package com.kauth.messaging;

import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class MessagingHelper {

    private final Plugin plugin;

    public MessagingHelper(Plugin plugin) {
        this.plugin = plugin;
    }

    public void sendLogin(Player player) {
        send(player, new AuthMessage(MessageConstants.LOGIN, player.getUniqueId().toString(), player.getName()));
    }

    public void sendLogout(Player player) {
        send(player, new AuthMessage(MessageConstants.LOGOUT, player.getUniqueId().toString(), player.getName()));
    }

    public void sendForceLogout(Player player) {
        send(player, new AuthMessage(MessageConstants.FORCE_LOGOUT, player.getUniqueId().toString(), player.getName()));
    }

    private void send(Player player, AuthMessage msg) {
        if (!plugin.getConfig().getBoolean("velocity.enabled", false)) return;
        try {
            player.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes());
        } catch (Exception e) {
            plugin.getLogger().warning("[Messaging] Mesaj gönderilemedi: " + e.getMessage());
        }
    }
}
