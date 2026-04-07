package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class RegisterCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;

    public RegisterCommand(KAuth plugin, AuthService auth, ConfigManager config) {
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

        if (auth.isRegistered(player.getName())) {
            player.sendMessage(config.msgComponent("chat-register.already"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(config.msgComponent("chat-register.usage"));
            return true;
        }

        AuthService.RegisterResult result = auth.register(player, args[0], args[1]);

        switch (result) {
            case SUCCESS -> {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                try { player.closeDialog(); } catch (Exception ignored) {}
                player.sendMessage(config.msgComponent("chat-register.success"));
                EffectUtil.playEffect(plugin, player, "register-success");
                logAction("register", player);
            }
            case PASSWORD_MISMATCH -> player.sendMessage(config.msgComponent("register.mismatch"));
            case PASSWORD_TOO_SHORT -> {
                String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                player.sendMessage(config.parse(msg));
            }
            case PASSWORD_TOO_LONG -> {
                String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                player.sendMessage(config.parse(msg));
            }
            default -> player.sendMessage(config.msgComponent("register.failed"));
        }
        return true;
    }

    private void logAction(String action, Player player) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return;
        String format = plugin.getConfig().getString("logging." + action, "");
        if (format.isEmpty()) return;
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        format = format.replace("%player%", player.getName()).replace("%ip%", ip);
        plugin.getLogger().info(format);
    }
}
