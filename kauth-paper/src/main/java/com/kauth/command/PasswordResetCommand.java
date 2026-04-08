package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.VerificationDialogFactory;
import com.kauth.email.EmailService;
import com.kauth.email.VerificationManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

        // Faz 2: /sifresifirla onayla <kod> <yenisifre>
        if (args[0].equalsIgnoreCase("onayla") || args[0].equalsIgnoreCase("confirm")) {
            if (args.length < 3) {
                player.sendMessage(config.msgComponent("password-reset.usage"));
                return true;
            }

            String code = args[1];
            String newPassword = args[2];
            String targetUsername = player.getName(); // Kendi hesabı

            // Pending reset var mı?
            if (!verificationManager.hasPendingReset(targetUsername)) {
                player.sendMessage(config.msgComponent("password-reset.no-pending"));
                return true;
            }

            // Yeni şifre validasyonu
            int minLen = auth.getMinPasswordLength();
            int maxLen = auth.getMaxPasswordLength();
            if (newPassword.length() < minLen) {
                String msg = config.msg("register.too_short").replace("%min%", String.valueOf(minLen));
                player.sendMessage(config.parse(msg));
                return true;
            }
            if (newPassword.length() > maxLen) {
                String msg = config.msg("register.too_long").replace("%max%", String.valueOf(maxLen));
                player.sendMessage(config.parse(msg));
                return true;
            }

            if (verificationManager.verifyReset(targetUsername, code)) {
                if (auth.changePassword(targetUsername, newPassword)) {
                    player.sendMessage(config.msgComponent("password-reset.success"));
                    logAction("password-changed", player);
                } else {
                    player.sendMessage(config.msgComponent("register.failed"));
                }
            } else {
                player.sendMessage(config.msgComponent("password-reset.invalid-code"));
            }
            return true;
        }

        // Faz 1: /sifresifirla <kullaniciadi>
        String targetUsername = args[0];

        if (!auth.isRegistered(targetUsername)) {
            player.sendMessage(config.msgComponent("password-reset.not-found"));
            return true;
        }

        String email = auth.getDb().getEmail(targetUsername);
        if (email == null || email.isEmpty()) {
            player.sendMessage(config.msgComponent("password-reset.no-email"));
            return true;
        }

        if (!auth.getDb().isEmailVerified(targetUsername)) {
            player.sendMessage(config.msgComponent("password-reset.email-not-verified"));
            return true;
        }

        // Rate limit
        if (!verificationManager.canSendEmail(player.getUniqueId())) {
            player.sendMessage(config.parse("<color:#FF6B6B>Lütfen bir dakika bekleyiniz!</color>"));
            return true;
        }

        String code = verificationManager.generateResetCode(targetUsername, email);
        emailService.sendPasswordResetCode(email, code);

        String maskedEmail = VerificationDialogFactory.maskEmail(email);
        player.sendMessage(config.parse(
                config.msg("password-reset.code-sent").replace("%email%", maskedEmail)));
        player.sendMessage(config.parse(
                "<color:#B0C4D4>Kodu girdikten sonra: <color:#4AEAFF><bold>/sifresifirla onayla <kod> <yenişifre></bold></color></color>"));

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
