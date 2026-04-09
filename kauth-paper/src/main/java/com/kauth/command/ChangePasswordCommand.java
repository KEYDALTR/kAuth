package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChangePasswordCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;

    public ChangePasswordCommand(KAuth plugin, AuthService auth, ConfigManager config) {
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

        if (args.length < 2) {
            player.sendMessage(config.msgComponent("changepassword-self.usage"));
            return true;
        }

        final String oldPassword = args[0];
        final String newPassword = args[1];

        // Async: DB + PBKDF2
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!auth.isRegistered(player.getName())) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(config.msgComponent("changepassword-self.not-registered")));
                return;
            }

            if (!auth.checkPassword(player.getName(), oldPassword)) {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(config.msgComponent("changepassword-self.wrong-old")));
                return;
            }

            boolean success = auth.changePassword(player.getName(), newPassword);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                if (success) {
                    player.sendMessage(config.msgComponent("changepassword-self.success"));
                    plugin.getLogger().info("[kAuth] " + player.getName() + " şifresini değiştirdi.");
                } else {
                    player.sendMessage(config.msgComponent("register.failed"));
                }
            });
        });
        return true;
    }
}
