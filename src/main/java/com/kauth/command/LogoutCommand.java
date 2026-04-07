package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class LogoutCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final DialogProvider dialogProvider;

    public LogoutCommand(KAuth plugin, AuthService auth, ConfigManager config, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.dialogProvider = dialogProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msgComponent("admin.player-only"));
            return true;
        }
        if (!auth.isAuthenticated(player)) {
            player.sendMessage(config.msgComponent("logout.not-logged-in"));
            return true;
        }

        auth.setAuthenticated(player, false);
        player.sendMessage(config.msgComponent("logout.success"));
        EffectUtil.playEffect(plugin, player, "logout");

        if (plugin.getConfig().getBoolean("auth.blind-effect", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) dialogProvider.showLogin(player);
        }, 5L);

        // Loglama
        if (plugin.getConfig().getBoolean("logging.enabled", true)) {
            String format = plugin.getConfig().getString("logging.logout", "");
            if (!format.isEmpty()) {
                plugin.getLogger().info(format.replace("%player%", player.getName()));
            }
        }
        return true;
    }
}
