package com.kauth.messaging;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PaperMessageListener implements PluginMessageListener {

    private final KAuth plugin;

    public PaperMessageListener(KAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] message) {
        if (!channel.equals(MessageConstants.CHANNEL)) return;

        AuthMessage msg = AuthMessage.fromBytes(message);
        UUID uuid = UUID.fromString(msg.playerUuid());

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || !target.isOnline()) return;

            AuthService auth = plugin.getAuthService();

            switch (msg.type()) {
                case MessageConstants.LOGIN -> {
                    if (!auth.isAuthenticated(target)) {
                        auth.forceLoginRemote(target);
                        target.removePotionEffect(PotionEffectType.BLINDNESS);
                        plugin.getDialogProvider().closeDialog(target);
                        plugin.getLogger().info("[Velocity] " + msg.playerName() + " uzaktan giriş yaptı.");
                    }
                }
                case MessageConstants.LOGOUT -> {
                    if (auth.isAuthenticated(target)) {
                        auth.removePlayer(uuid);
                        plugin.getLogger().info("[Velocity] " + msg.playerName() + " uzaktan çıkış yaptı.");
                    }
                }
                case MessageConstants.FORCE_LOGOUT -> {
                    auth.removePlayer(uuid);
                    plugin.getDialogProvider().showLogin(target);
                    plugin.getLogger().info("[Velocity] " + msg.playerName() + " zorla çıkış yapıldı.");
                }
            }
        });
    }
}
