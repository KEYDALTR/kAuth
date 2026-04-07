package com.kauth.dialog;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.util.EffectUtil;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

public final class DialogCallbacks {

    private DialogCallbacks() {}

    public static DialogActionCallback loginCallback(Plugin plugin, ConfigManager config, LoginDialogFactory loginFactory) {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) return;

            String password = response.getText("password");
            if (password == null || password.isEmpty()) {
                safeShowDialog(player, loginFactory.build(player, config.msgComponent("login.empty_password")));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                AuthService auth = ((KAuth) plugin).getAuthService();

                if (!auth.isRegistered(player.getName())) {
                    player.sendMessage(config.msgComponent("login.need_register"));
                    safeCloseDialog(player);
                    return;
                }

                if (auth.isIpBlocked(player)) {
                    player.kick(config.parse("<color:#FF6B6B>IP adresiniz geçici olarak engellendi.</color>"));
                    return;
                }

                if (auth.checkPassword(player.getName(), password)) {
                    auth.forceLogin(player);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                    safeCloseDialog(player);
                    player.sendMessage(config.msgComponent("login.success"));

                    String lastInfo = auth.getLastLoginInfo(player.getName());
                    if (lastInfo != null) {
                        player.sendMessage(config.parse("<color:#3A4F6A>Son giriş: " + lastInfo + "</color>"));
                    }

                    EffectUtil.playEffect(plugin, player, "login-success");
                    log(plugin, "login", player);
                } else {
                    int attempts = auth.incrementAttempts(player);
                    int maxAttempts = auth.getMaxAttempts();
                    log(plugin, "failed-login", player);

                    if (maxAttempts > 0 && attempts >= maxAttempts) {
                        String msg = plugin.getConfig().getString("auth.max-attempts-kick-message",
                                "<red>Çok fazla yanlış deneme!</red>");
                        log(plugin, "kick-attempts", player);
                        player.kick(config.parse(msg));
                        return;
                    }

                    EffectUtil.playEffect(plugin, player, "login-fail");

                    int remaining = auth.getRemainingAttempts(player);
                    if (remaining > 0) {
                        String attemptsMsg = config.msg("login.attempts-left")
                                .replace("%attempts%", String.valueOf(remaining));
                        safeShowDialog(player, loginFactory.build(player, config.parse(
                                config.msg("login.wrong_password") + "<br>" + attemptsMsg)));
                    } else {
                        safeShowDialog(player, loginFactory.build(player, config.msgComponent("login.wrong_password")));
                    }
                }
            });
        };
    }

    public static DialogActionCallback loginCancelCallback(ConfigManager config) {
        return (response, audience) -> {
            if (audience instanceof Player player) {
                player.kick(config.msgComponent("login.cancelled"));
            }
        };
    }

    public static DialogActionCallback registerCallback(Plugin plugin, ConfigManager config, RegisterDialogFactory registerFactory) {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) return;

            String password = response.getText("password");
            String confirmPassword = response.getText("confirm_password");

            if (password == null || password.isEmpty() || confirmPassword == null || confirmPassword.isEmpty()) {
                safeShowDialog(player, registerFactory.build(player, config.msgComponent("register.need_both")));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                AuthService auth = ((KAuth) plugin).getAuthService();
                AuthService.RegisterResult result = auth.register(player, password, confirmPassword);

                switch (result) {
                    case SUCCESS -> {
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        safeCloseDialog(player);
                        player.sendMessage(config.msgComponent("register.success"));
                        EffectUtil.playEffect(plugin, player, "register-success");
                        log(plugin, "register", player);
                    }
                    case ALREADY_REGISTERED -> safeShowDialog(player, registerFactory.build(player, config.msgComponent("register.already")));
                    case PASSWORD_MISMATCH -> safeShowDialog(player, registerFactory.build(player, config.msgComponent("register.mismatch")));
                    case PASSWORD_TOO_SHORT -> {
                        String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                        safeShowDialog(player, registerFactory.build(player, config.parse(msg)));
                    }
                    case PASSWORD_TOO_LONG -> {
                        String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                        safeShowDialog(player, registerFactory.build(player, config.parse(msg)));
                    }
                    case PASSWORD_INVALID -> safeShowDialog(player, registerFactory.build(player, config.msgComponent("register.invalid")));
                    case IP_LIMIT -> safeShowDialog(player, registerFactory.build(player, config.parse(
                            "<color:#FF6B6B>Bu IP adresinden maksimum hesap sayısına ulaşıldı!</color>")));
                    default -> safeShowDialog(player, registerFactory.build(player, config.msgComponent("register.failed")));
                }
            });
        };
    }

    public static DialogActionCallback registerCancelCallback(ConfigManager config) {
        return (response, audience) -> {
            if (audience instanceof Player player) {
                player.kick(config.msgComponent("register.cancelled"));
            }
        };
    }

    public static DialogActionCallback ruleConfirmCallback(Plugin plugin, ConfigManager config) {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) return;

            if (config.ruleAgreeEnabled()) {
                Boolean agreed = response.getBoolean(config.ruleAgreeKey());
                if (agreed == null || !agreed) {
                    String msg = plugin.getConfig().getString("rule.decline-kick-message",
                            "<red>Kuralları kabul etmediniz.</red>");
                    log(plugin, "rule-declined", player);
                    player.kick(config.parse(msg));
                    return;
                }
            }

            log(plugin, "rule-accepted", player);
            safeCloseDialog(player);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                RegisterDialogFactory registerFactory = new RegisterDialogFactory(plugin, config);
                safeShowDialog(player, registerFactory.build(player));
            });
        };
    }

    private static void safeShowDialog(Player player, Object dialog) {
        try {
            player.showDialog((io.papermc.paper.dialog.Dialog) dialog);
        } catch (Exception ignored) {}
    }

    private static void safeCloseDialog(Player player) {
        try { player.closeDialog(); } catch (Exception ignored) {}
    }

    private static void log(Plugin plugin, String action, Player player) {
        if (!plugin.getConfig().getBoolean("logging.enabled", true)) return;
        String format = plugin.getConfig().getString("logging." + action, "");
        if (format.isEmpty()) return;
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
        String date = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        format = format.replace("%player%", player.getName()).replace("%ip%", ip).replace("%date%", date);
        KAuth kauth = (KAuth) plugin;
        AuthService auth = kauth.getAuthService();
        int remaining = auth.getRemainingAttempts(player);
        int max = auth.getMaxAttempts();
        format = format.replace("%attempts%", String.valueOf(max > 0 ? max - remaining : 0));
        plugin.getLogger().info(format);
    }
}
