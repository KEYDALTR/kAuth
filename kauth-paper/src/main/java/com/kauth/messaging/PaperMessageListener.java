package com.kauth.messaging;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.kauth.config.Settings;
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

        Settings.Velocity v = plugin.getConfigManager().getSettings().get().velocity();
        if (!v.enabled()) return;
        if (!v.hasSecret()) {
            plugin.getLogger().warning("[Güvenlik] Plugin mesajı alındı ama velocity.secret ayarlı değil, reddediliyor");
            return;
        }

        final AuthMessage msg;
        try {
            msg = AuthMessage.fromBytes(message, v.secret());
        } catch (AuthMessage.MessageVerificationException e) {
            plugin.getLogger().warning("[Güvenlik] Geçersiz plugin mesajı (" + carrier.getName()
                    + "): " + e.getMessage());
            return;
        }

        final UUID uuid;
        try {
            uuid = UUID.fromString(msg.playerUuid());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Güvenlik] Geçersiz UUID mesajda: " + msg.playerUuid());
            return;
        }

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
                        plugin.getJoinListener().beginAuthFlow(target);
                        plugin.getLogger().info("[Velocity] " + msg.playerName() + " uzaktan çıkış yaptı.");
                    }
                }
                case MessageConstants.FORCE_LOGOUT -> {
                    auth.removePlayer(uuid);
                    auth.clearSession(uuid);
                    plugin.getJoinListener().beginAuthFlow(target);
                    plugin.getLogger().info("[Velocity] " + msg.playerName() + " zorla çıkış yapıldı.");
                }
                default -> plugin.getLogger().warning("[Velocity] Bilinmeyen mesaj tipi: " + msg.type());
            }
        });
    }
}
