package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Level;

public class LoginCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;

    public LoginCommand(KAuth plugin, AuthService auth, ConfigManager config) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msgComponent("admin.player-only"));
            return true;
        }

        if (auth.isAuthenticated(player)) {
            player.sendMessage(config.msgComponent("chat-login.already"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(config.msgComponent("chat-login.usage"));
            return true;
        }

        final String password = args[0];
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Boolean valid;
            boolean registered;
            try {
                registered = auth.isRegistered(player.getName());
                valid = registered && auth.checkPassword(player.getName(), password);
            } catch (DataAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "[kAuth] Login DB hatası", e);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(config.parse(
                                "<color:#FF6B6B>Veritabanı hatası, lütfen tekrar deneyiniz.</color>"));
                    }
                });
                return;
            }

            final boolean isRegistered = registered;
            final boolean isValid = valid;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (!isRegistered) {
                    player.sendMessage(config.msgComponent("login.need_register"));
                    return;
                }
                if (isValid) {
                    auth.forceLogin(player);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    try { player.closeDialog(); } catch (Exception ignored) {}
                    player.sendMessage(config.msgComponent("chat-login.success"));
                    EffectUtil.playEffect(plugin, player, "login-success");
                    logAction("login", player);
                } else {
                    EffectUtil.playEffect(plugin, player, "login-fail");
                    int attempts = auth.incrementAttempts(player);
                    int max = auth.getMaxAttempts();
                    if (max > 0 && attempts >= max) {
                        String msg = plugin.getConfig().getString("auth.max-attempts-kick-message",
                                "<red>Çok fazla yanlış deneme!</red>");
                        logAction("kick-attempts", player);
                        player.kick(config.parse(msg));
                        return;
                    }
                    player.sendMessage(config.msgComponent("login.wrong_password"));
                    logAction("failed-login", player);
                }
            });
        });
        return true;
    }

    private void logAction(String action, Player player) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return;
        String format = plugin.getConfig().getString("logging." + action, "");
        if (format.isEmpty()) return;
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        int attempts = auth.getUsedAttempts(player);
        format = format.replace("%player%", player.getName())
                       .replace("%ip%", ip)
                       .replace("%attempts%", String.valueOf(attempts));
        plugin.getLogger().info(format);
    }
}
