package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

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

        AuthService.PasswordValidationResult validation = auth.validatePassword(player.getName(), newPassword);
        switch (validation) {
            case TOO_SHORT -> {
                String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                player.sendMessage(config.parse(msg));
                return true;
            }
            case TOO_LONG -> {
                String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                player.sendMessage(config.parse(msg));
                return true;
            }
            case WEAK -> {
                player.sendMessage(config.msgComponent("register.invalid"));
                return true;
            }
            case OK -> {}
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean registered;
            final boolean correctOld;
            final boolean success;
            try {
                registered = auth.isRegistered(player.getName());
                if (!registered) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(config.msgComponent("changepassword-self.not-registered")));
                    return;
                }

                correctOld = auth.checkPassword(player.getName(), oldPassword);
                if (!correctOld) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            player.sendMessage(config.msgComponent("changepassword-self.wrong-old")));
                    return;
                }

                success = auth.changePassword(player.getName(), newPassword);
            } catch (DataAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "[kAuth] ChangePassword DB hatası", e);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(config.parse(
                                "<color:#FF6B6B>Veritabanı hatası, lütfen tekrar deneyiniz.</color>"));
                    }
                });
                return;
            }

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
