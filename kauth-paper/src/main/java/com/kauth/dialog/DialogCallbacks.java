package com.kauth.dialog;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.email.EmailService;
import com.kauth.email.VerificationManager;
import com.kauth.util.EffectUtil;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Level;

public final class DialogCallbacks {

    private DialogCallbacks() {}

    public static DialogActionCallback loginCallback(Plugin plugin, ConfigManager config, LoginDialogFactory loginFactory) {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) return;

            String password = response.getText("password");
            if (password == null || password.isEmpty()) {
                safeShowDialog(plugin, player, loginFactory.build(player, config.msgComponent("login.empty_password")));
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                AuthService auth = ((KAuth) plugin).getAuthService();

                boolean registered;
                boolean ipBlocked;
                boolean passwordValid;
                String lastInfo = null;
                try {
                    registered = auth.isRegistered(player.getName());
                    ipBlocked = auth.isIpBlocked(player);
                    passwordValid = registered && auth.checkPassword(player.getName(), password);
                    if (passwordValid) lastInfo = auth.getLastLoginInfo(player.getName());
                } catch (DataAccessException e) {
                    plugin.getLogger().log(Level.SEVERE, "[kAuth] Login dialog DB hatası", e);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            safeShowDialog(plugin, player, loginFactory.build(player,
                                    config.parse("<color:#FF6B6B>Veritabanı hatası, tekrar deneyiniz.</color>")));
                        }
                    });
                    return;
                }

                final boolean regF = registered;
                final boolean blockedF = ipBlocked;
                final boolean validF = passwordValid;
                final String lastInfoF = lastInfo;

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) return;

                    if (!regF) {
                        player.sendMessage(config.msgComponent("login.need_register"));
                        safeCloseDialog(plugin, player);
                        return;
                    }

                    if (blockedF) {
                        player.kick(config.parse("<color:#FF6B6B>IP adresiniz geçici olarak engellendi.</color>"));
                        return;
                    }

                    if (validF) {
                        auth.forceLogin(player);
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        safeCloseDialog(plugin, player);
                        player.sendMessage(config.msgComponent("login.success"));

                        if (lastInfoF != null) {
                            player.sendMessage(config.parse("<color:#3A4F6A>Son giriş: " + lastInfoF + "</color>"));
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
                            safeShowDialog(plugin, player, loginFactory.build(player, config.parse(
                                    config.msg("login.wrong_password") + "<br>" + attemptsMsg)));
                        } else {
                            safeShowDialog(plugin, player, loginFactory.build(player, config.msgComponent("login.wrong_password")));
                        }
                    }
                });
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
            String email = response.getText("email");

            boolean requireConfirm = config.getSettings().get().auth().requirePasswordConfirmation();
            String confirmPassword = requireConfirm ? response.getText("confirm_password") : password;

            if (password == null || password.isEmpty()) {
                safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.need_both")));
                return;
            }
            if (requireConfirm && (confirmPassword == null || confirmPassword.isEmpty())) {
                safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.need_both")));
                return;
            }

            KAuth kauth = (KAuth) plugin;
            boolean emailRequired = kauth.getEmailService().isEnabled()
                    && config.getSettings().get().email().verificationRequired()
                    && config.getSettings().get().email().inputEnabled();

            if (emailRequired && (email == null || email.isEmpty())) {
                safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.email_required")));
                return;
            }

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                AuthService auth = kauth.getAuthService();
                AuthService.RegisterResult result;
                try {
                    result = auth.register(player, password, confirmPassword, email);
                } catch (DataAccessException e) {
                    plugin.getLogger().log(Level.SEVERE, "[kAuth] Register dialog DB hatası", e);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            safeShowDialog(plugin, player, registerFactory.build(player,
                                    config.parse("<color:#FF6B6B>Veritabanı hatası, tekrar deneyiniz.</color>")));
                        }
                    });
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> handleRegisterResult(
                        plugin, config, kauth, registerFactory, player, email, result));
            });
        };
    }

    private static void handleRegisterResult(Plugin plugin, ConfigManager config, KAuth kauth,
                                               RegisterDialogFactory registerFactory, Player player,
                                               String email, AuthService.RegisterResult result) {
        if (!player.isOnline()) return;
        AuthService auth = kauth.getAuthService();
        switch (result) {
            case SUCCESS -> {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                safeCloseDialog(plugin, player);
                player.sendMessage(config.msgComponent("register.success"));
                EffectUtil.playEffect(plugin, player, "register-success");
                log(plugin, "register", player);
            }
            case PENDING_EMAIL_VERIFICATION -> {
                VerificationManager vm = kauth.getVerificationManager();
                EmailService es = kauth.getEmailService();

                String code = vm.generateVerificationCode(player.getUniqueId(), email);
                es.sendVerificationCode(email, code).whenComplete((ok, err) -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (ok != null && ok) {
                            String maskedEmail = VerificationDialogFactory.maskEmail(email);
                            player.sendMessage(config.parse(
                                    "<color:#4AEAFF>Doğrulama kodu " + maskedEmail + " adresine gönderildi.</color>"));

                            if (kauth.getDialogProvider().supportsDialog(player)) {
                                VerificationDialogFactory vdf = new VerificationDialogFactory(plugin, config);
                                safeShowDialog(plugin, player, vdf.build(player, maskedEmail));
                            } else {
                                player.sendMessage(config.parse(
                                        "<color:#B0C4D4>Kodu girmek için: <color:#4AEAFF><bold>/dogrula <kod></bold></color></color>"));
                            }
                        } else {
                            vm.removePendingVerification(player.getUniqueId());
                            player.sendMessage(config.parse(
                                    "<color:#FF6B6B>E-posta gönderilemedi! Lütfen daha sonra tekrar deneyiniz.</color>"));
                            safeCloseDialog(plugin, player);
                        }
                    });
                });
            }
            case ALREADY_REGISTERED -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.already")));
            case PASSWORD_MISMATCH -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.mismatch")));
            case PASSWORD_TOO_SHORT -> {
                String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                safeShowDialog(plugin, player, registerFactory.build(player, config.parse(msg)));
            }
            case PASSWORD_TOO_LONG -> {
                String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                safeShowDialog(plugin, player, registerFactory.build(player, config.parse(msg)));
            }
            case PASSWORD_INVALID -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.invalid")));
            case EMAIL_INVALID -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.email_invalid")));
            case EMAIL_ALREADY_USED -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.email_already_used")));
            case IP_LIMIT -> safeShowDialog(plugin, player, registerFactory.build(player, config.parse(
                    "<color:#FF6B6B>Bu IP adresinden maksimum hesap sayısına ulaşıldı!</color>")));
            default -> safeShowDialog(plugin, player, registerFactory.build(player, config.msgComponent("register.failed")));
        }
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
            safeCloseDialog(plugin, player);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                RegisterDialogFactory registerFactory = new RegisterDialogFactory(plugin, config);
                safeShowDialog(plugin, player, registerFactory.build(player));
            });
        };
    }

    public static DialogActionCallback verificationCallback(Plugin plugin, ConfigManager config,
                                                             VerificationDialogFactory vdf, String maskedEmail) {
        return (response, audience) -> {
            if (!(audience instanceof Player player)) return;

            String code = response.getText("verification_code");
            if (code == null || code.isEmpty()) {
                safeShowDialog(plugin, player, vdf.build(player, maskedEmail,
                        config.parse("<color:#FF6B6B>Lütfen doğrulama kodunu giriniz!</color>")));
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                KAuth kauth = (KAuth) plugin;
                VerificationManager vm = kauth.getVerificationManager();
                AuthService auth = kauth.getAuthService();

                VerificationManager.VerifyResult result = vm.verifyEmail(player.getUniqueId(), code.trim());
                switch (result) {
                    case OK -> {
                        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                            try {
                                auth.getDb().setEmailVerified(player.getName(), true);
                            } catch (DataAccessException e) {
                                plugin.getLogger().log(Level.SEVERE, "[kAuth] verificationCallback DB hatası", e);
                                plugin.getServer().getScheduler().runTask(plugin, () -> {
                                    if (player.isOnline()) {
                                        player.kick(config.parse("<color:#FF6B6B>Veritabanı hatası, tekrar bağlanınız.</color>"));
                                    }
                                });
                                return;
                            }
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                auth.forceLogin(player);
                                player.removePotionEffect(PotionEffectType.BLINDNESS);
                                safeCloseDialog(plugin, player);
                                player.sendMessage(config.msgComponent("register.success"));
                                player.sendMessage(config.parse("<color:#4AEAFF>E-posta adresiniz doğrulandı!</color>"));
                                EffectUtil.playEffect(plugin, player, "register-success");
                                log(plugin, "register", player);
                            });
                        });
                    }
                    case WRONG -> safeShowDialog(plugin, player, vdf.build(player, maskedEmail,
                            config.parse("<color:#FF6B6B>Yanlış kod! Tekrar deneyiniz.</color>")));
                    case EXPIRED -> {
                        safeCloseDialog(plugin, player);
                        player.kick(config.parse("<color:#FF6B6B>Doğrulama kodu süresi doldu. Lütfen tekrar bağlanınız.</color>"));
                    }
                    case NO_PENDING -> safeShowDialog(plugin, player, vdf.build(player, maskedEmail,
                            config.parse("<color:#FF6B6B>Bekleyen kod bulunamadı!</color>")));
                    case TOO_MANY_ATTEMPTS -> {
                        safeCloseDialog(plugin, player);
                        player.kick(config.parse("<color:#FF6B6B>Çok fazla yanlış kod girdiniz! Lütfen tekrar bağlanınız.</color>"));
                    }
                }
            });
        };
    }

    private static void safeShowDialog(Plugin plugin, Player player, Object dialog) {
        try {
            player.showDialog((io.papermc.paper.dialog.Dialog) dialog);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "[kAuth] safeShowDialog hatası (" + player.getName() + "): " + e.getMessage(), e);
            player.sendMessage(net.kyori.adventure.text.Component.text(
                    "§cDialog gösterilemedi. Lütfen tekrar deneyin: /giris <şifre> veya /kayit <şifre>"));
        }
    }

    private static void safeCloseDialog(Plugin plugin, Player player) {
        try { player.closeDialog(); }
        catch (Exception e) {
            plugin.getLogger().warning("[kAuth] closeDialog hatası: " + e.getMessage());
        }
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
        format = format.replace("%attempts%", String.valueOf(auth.getUsedAttempts(player)));
        plugin.getLogger().info(format);
    }
}
