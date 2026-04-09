package com.kauth.messaging;

import com.kauth.KAuth;
import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.kauth.config.Settings;
import org.bukkit.entity.Player;

public class MessagingHelper {

    private final KAuth plugin;

    public MessagingHelper(KAuth plugin) {
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
        Settings.Velocity v = plugin.getConfigManager().getSettings().get().velocity();
        if (!v.enabled() || !v.hasSecret()) return;
        try {
            player.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes(v.secret()));
        } catch (Exception e) {
            plugin.getLogger().warning("[Messaging] Mesaj gönderilemedi: " + e.getMessage());
        }
    }
}
