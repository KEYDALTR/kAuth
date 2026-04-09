package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.VerificationDialogFactory;
import com.kauth.email.EmailService;
import com.kauth.email.VerificationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class PasswordResetCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final EmailService emailService;
    private final VerificationManager verificationManager;

    public PasswordResetCommand(KAuth plugin, AuthService auth, ConfigManager config,
                                 EmailService emailService, VerificationManager verificationManager) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.emailService = emailService;
        this.verificationManager = verificationManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msgComponent("admin.player-only"));
            return true;
        }

        if (!emailService.isEnabled()) {
            player.sendMessage(config.parse("<color:#FF6B6B>E-posta sistemi aktif değil!</color>"));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(config.msgComponent("password-reset.usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("onayla") || args[0].equalsIgnoreCase("confirm")) {
            handleConfirm(player, args);
            return true;
        }

        String requestedUsername = args[0];
        String targetUsername = player.getName();
        if (!requestedUsername.equalsIgnoreCase(targetUsername)) {
            player.sendMessage(config.parse(
                    "<color:#FF6B6B>Sadece kendi hesabınız için şifre sıfırlayabilirsiniz.</color>"));
            return true;
        }

        handleRequest(player, targetUsername);
        return true;
    }

    private void handleRequest(Player player, String targetUsername) {
        if (!verificationManager.canSendEmail(player.getUniqueId())) {
            player.sendMessage(config.parse("<color:#FF6B6B>Lütfen bir dakika bekleyiniz!</color>"));
            return;
        }
        if (!verificationManager.canRequestResetForTarget(targetUsername)) {
            player.sendMessage(config.parse("<color:#FF6B6B>Bu hesap için yakın zamanda kod istenmiş, lütfen bekleyiniz.</color>"));
            return;
        }

        final String genericMsg = "<color:#4AEAFF>Hesap kayıtlı ve e-posta doğrulanmışsa sıfırlama kodu gönderildi.</color>";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean registered;
            String email;
            boolean verified;
            try {
                registered = auth.isRegistered(targetUsername);
                email = registered ? auth.getDb().getEmail(targetUsername) : null;
                verified = registered && auth.getDb().isEmailVerified(targetUsername);
            } catch (DataAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "[kAuth] PasswordReset DB hatası", e);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(config.parse(
                                "<color:#FF6B6B>Veritabanı hatası, lütfen tekrar deneyiniz.</color>"));
                    }
                });
                return;
            }

            final boolean isReg = registered;
            final boolean isVer = verified;
            final String emailAddr = email;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(config.parse(genericMsg));

                if (isReg && isVer && emailAddr != null && !emailAddr.isEmpty()) {
                    String code = verificationManager.generateResetCode(targetUsername, emailAddr);
                    emailService.sendPasswordResetCode(emailAddr, code).whenComplete((ok, err) -> {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) return;
                            if (ok != null && ok) {
                                String masked = VerificationDialogFactory.maskEmail(emailAddr);
                                player.sendMessage(config.parse(
                                        "<color:#B0C4D4>Kod e-postanıza gönderildi: <color:#4AEAFF>" + masked + "</color></color>"));
                                player.sendMessage(config.parse(
                                        "<color:#B0C4D4>Kodu girmek için: <color:#4AEAFF><bold>/sifresifirla onayla <kod> <yenişifre></bold></color></color>"));
                            } else {
                                player.sendMessage(config.parse(
                                        "<color:#FF6B6B>E-posta gönderilemedi, lütfen daha sonra tekrar deneyiniz.</color>"));
                            }
                        });
                    });
                }
            });
        });
    }

    private void handleConfirm(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(config.msgComponent("password-reset.usage"));
            return;
        }

        String code = args[1];
        String newPassword = args[2];
        String targetUsername = player.getName();

        if (!verificationManager.hasPendingReset(targetUsername)) {
            player.sendMessage(config.msgComponent("password-reset.no-pending"));
            return;
        }

        AuthService.PasswordValidationResult pv = auth.validatePassword(targetUsername, newPassword);
        switch (pv) {
            case TOO_SHORT -> {
                String msg = config.msg("register.too_short").replace("%min%", String.valueOf(auth.getMinPasswordLength()));
                player.sendMessage(config.parse(msg));
                return;
            }
            case TOO_LONG -> {
                String msg = config.msg("register.too_long").replace("%max%", String.valueOf(auth.getMaxPasswordLength()));
                player.sendMessage(config.parse(msg));
                return;
            }
            case WEAK -> {
                player.sendMessage(config.msgComponent("register.invalid"));
                return;
            }
            case OK -> {}
        }

        VerificationManager.VerifyResult result = verificationManager.verifyReset(targetUsername, code);
        switch (result) {
            case OK -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    boolean success;
                    try {
                        success = auth.changePassword(targetUsername, newPassword);
                    } catch (DataAccessException e) {
                        plugin.getLogger().log(Level.SEVERE, "[kAuth] PasswordReset changePassword hatası", e);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.sendMessage(config.parse(
                                        "<color:#FF6B6B>Veritabanı hatası!</color>"));
                            }
                        });
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        if (success) {
                            auth.clearSession(player.getUniqueId());
                            player.sendMessage(config.msgComponent("password-reset.success"));
                            logAction("password-changed", player);
                        } else {
                            player.sendMessage(config.msgComponent("register.failed"));
                        }
                    });
                });
            }
            case WRONG -> player.sendMessage(config.msgComponent("password-reset.invalid-code"));
            case EXPIRED -> player.sendMessage(config.parse("<color:#FF6B6B>Kod süresi doldu, yeniden isteyiniz.</color>"));
            case NO_PENDING -> player.sendMessage(config.msgComponent("password-reset.no-pending"));
            case TOO_MANY_ATTEMPTS -> player.sendMessage(config.parse(
                    "<color:#FF6B6B>Çok fazla yanlış deneme yaptınız! Kod iptal edildi, yeniden isteyiniz.</color>"));
        }
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
