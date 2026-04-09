package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class KAuthCommand implements TabExecutor {

    private final KAuth plugin;
    private final ConfigManager config;
    private final AuthService auth;
    private final DialogProvider dialogProvider;

    public KAuthCommand(KAuth plugin, ConfigManager config, AuthService auth, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.config = config;
        this.auth = auth;
        this.dialogProvider = dialogProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendUsage(sender); return true; }

        switch (args[0].toLowerCase()) {
            case "reload" -> {
                if (!sender.hasPermission("kauth.admin")) { sender.sendMessage(config.msgComponent("admin.no-permission")); return true; }
                config.reload();
                sender.sendMessage(config.msgComponent("admin.reload"));
            }
            case "cikis", "logout" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(config.msgComponent("admin.player-only")); return true; }
                if (!auth.isAuthenticated(player)) { player.sendMessage(config.msgComponent("logout.not-logged-in")); return true; }
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        auth.setAuthenticated(player, false);
                    } catch (DataAccessException e) {
                        plugin.getLogger().log(Level.SEVERE, "[kAuth] cikis DB hatası", e);
                        auth.removePlayer(player.getUniqueId());
                        auth.clearSession(player.getUniqueId());
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        player.sendMessage(config.msgComponent("logout.success"));
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline()) plugin.getJoinListener().beginAuthFlow(player);
                        }, 5L);
                    });
                });
            }
            case "ac", "open" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage(config.msgComponent("admin.player-only")); return true; }
                if (args.length < 2) { sender.sendMessage(config.msgComponent("admin.usage-open")); return true; }
                switch (args[1].toLowerCase()) {
                    case "giris", "login" -> dialogProvider.showLogin(player);
                    case "kayit", "register" -> dialogProvider.showRegister(player);
                    default -> sender.sendMessage(config.msgComponent("admin.usage-open"));
                }
            }
            case "kayitsil", "unregister" -> {
                if (!sender.hasPermission("kauth.admin")) { sender.sendMessage(config.msgComponent("admin.no-permission")); return true; }
                if (args.length < 2) { sender.sendMessage(config.msgComponent("admin.usage-unregister")); return true; }
                final String targetName = args[1];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean deleted;
                    try {
                        deleted = auth.deleteUser(targetName);
                    } catch (DataAccessException e) {
                        plugin.getLogger().log(Level.SEVERE, "[kAuth] kayitsil DB hatası", e);
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                sender.sendMessage(config.parse("<color:#FF6B6B>Veritabanı hatası!</color>")));
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (deleted) {
                            sender.sendMessage(config.parse(config.msg("admin.unregister-success").replace("%player%", targetName)));
                            auth.clearSession(targetName);
                            Player target = Bukkit.getPlayerExact(targetName);
                            if (target != null && target.isOnline()) {
                                auth.removePlayer(target.getUniqueId());
                                auth.clearSession(target.getUniqueId());
                                auth.sendForceLogout(target);
                                plugin.getJoinListener().beginAuthFlow(target);
                            }
                        } else {
                            sender.sendMessage(config.parse(config.msg("admin.unregister-fail").replace("%player%", targetName)));
                        }
                    });
                });
            }
            case "sifredegistir", "changepassword" -> {
                if (!sender.hasPermission("kauth.admin")) { sender.sendMessage(config.msgComponent("admin.no-permission")); return true; }
                if (args.length < 3) { sender.sendMessage(config.msgComponent("admin.usage-changepassword")); return true; }
                final String targetName = args[1];
                final String newPassword = args[2];

                AuthService.PasswordValidationResult pv = auth.validatePassword(targetName, newPassword);
                switch (pv) {
                    case TOO_SHORT -> {
                        String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                        sender.sendMessage(config.parse(msg));
                        return true;
                    }
                    case TOO_LONG -> {
                        String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                        sender.sendMessage(config.parse(msg));
                        return true;
                    }
                    case WEAK -> {
                        sender.sendMessage(config.msgComponent("register.invalid"));
                        return true;
                    }
                    case OK -> {}
                }

                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean changed;
                    try {
                        changed = auth.changePassword(targetName, newPassword);
                    } catch (DataAccessException e) {
                        plugin.getLogger().log(Level.SEVERE, "[kAuth] sifredegistir DB hatası", e);
                        plugin.getServer().getScheduler().runTask(plugin, () ->
                                sender.sendMessage(config.parse("<color:#FF6B6B>Veritabanı hatası!</color>")));
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (changed) {
                            sender.sendMessage(config.parse(config.msg("admin.changepassword-success").replace("%player%", targetName)));
                            auth.clearSession(targetName);
                            Player target = Bukkit.getPlayerExact(targetName);
                            if (target != null && target.isOnline()) {
                                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try { auth.setAuthenticated(target, false); }
                                    catch (DataAccessException ignored) { auth.removePlayer(target.getUniqueId()); }
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        auth.clearSession(target.getUniqueId());
                                        auth.sendForceLogout(target);
                                        plugin.getJoinListener().beginAuthFlow(target);
                                    });
                                });
                            }
                        } else {
                            sender.sendMessage(config.parse(config.msg("admin.changepassword-fail").replace("%player%", targetName)));
                        }
                    });
                });
            }
            default -> sendUsage(sender);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(config.msgComponent("admin.usage"));
        sender.sendMessage(config.msgComponent("admin.usage-login"));
        sender.sendMessage(config.msgComponent("admin.usage-register"));
        sender.sendMessage(config.msgComponent("admin.usage-logout"));
        sender.sendMessage(config.msgComponent("admin.usage-changepassword-self"));
        if (sender.hasPermission("kauth.admin")) {
            sender.sendMessage(config.msgComponent("admin.usage-reload"));
            sender.sendMessage(config.msgComponent("admin.usage-open"));
            sender.sendMessage(config.msgComponent("admin.usage-unregister"));
            sender.sendMessage(config.msgComponent("admin.usage-changepassword"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> c = new ArrayList<>();
        if (args.length == 1) {
            c.addAll(List.of("cikis", "ac"));
            if (sender.hasPermission("kauth.admin")) c.addAll(List.of("reload", "kayitsil", "sifredegistir"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("ac") || args[0].equalsIgnoreCase("open"))) {
            c.addAll(List.of("giris", "kayit"));
        }
        return c.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
    }
}
