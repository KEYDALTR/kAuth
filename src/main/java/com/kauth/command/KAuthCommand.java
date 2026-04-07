package com.kauth.command;

import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class KAuthCommand implements TabExecutor {

    private final ConfigManager config;
    private final AuthService auth;
    private final DialogProvider dialogProvider;

    public KAuthCommand(ConfigManager config, AuthService auth, DialogProvider dialogProvider) {
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
                auth.setAuthenticated(player, false);
                player.sendMessage(config.msgComponent("logout.success"));
                dialogProvider.showLogin(player);
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
                if (auth.deleteUser(args[1])) {
                    sender.sendMessage(config.parse(config.msg("admin.unregister-success").replace("%player%", args[1])));
                } else {
                    sender.sendMessage(config.parse(config.msg("admin.unregister-fail").replace("%player%", args[1])));
                }
            }
            case "sifredegistir", "changepassword" -> {
                if (!sender.hasPermission("kauth.admin")) { sender.sendMessage(config.msgComponent("admin.no-permission")); return true; }
                if (args.length < 3) { sender.sendMessage(config.msgComponent("admin.usage-changepassword")); return true; }
                if (auth.changePassword(args[1], args[2])) {
                    sender.sendMessage(config.parse(config.msg("admin.changepassword-success").replace("%player%", args[1])));
                } else {
                    sender.sendMessage(config.parse(config.msg("admin.changepassword-fail").replace("%player%", args[1])));
                }
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
