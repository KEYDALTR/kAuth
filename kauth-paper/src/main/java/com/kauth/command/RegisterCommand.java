package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.VerificationDialogFactory;
import com.kauth.email.EmailService;
import com.kauth.email.VerificationManager;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Level;

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

        boolean requireConfirm = plugin.getConfigManager().getSettings().get().auth().requirePasswordConfirmation();

        final String password;
        final String confirm;
        final String email;

        if (requireConfirm) {
            if (args.length < 2) {
                player.sendMessage(config.msgComponent("chat-register.usage"));
                return true;
            }
            password = args[0];
            confirm = args[1];
            email = args.length >= 3 ? args[2] : null;
        } else {
            if (args.length < 1) {
                player.sendMessage(config.msgComponent("chat-register.usage"));
                return true;
            }
            password = args[0];
            confirm = args[0];
            email = args.length >= 2 ? args[1] : null;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            final AuthService.RegisterResult result;
            try {
                result = auth.register(player, password, confirm, email);
            } catch (DataAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "[kAuth] Register DB hatası", e);
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
                switch (result) {
                    case SUCCESS -> {
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        try { player.closeDialog(); } catch (Exception ignored) {}
                        player.sendMessage(config.msgComponent("chat-register.success"));
                        EffectUtil.playEffect(plugin, player, "register-success");
                        logAction("register", player);
                    }
                    case PENDING_EMAIL_VERIFICATION -> {
                        VerificationManager vm = plugin.getVerificationManager();
                        EmailService es = plugin.getEmailService();
                        String code = vm.generateVerificationCode(player.getUniqueId(), email);
                        es.sendVerificationCode(email, code).whenComplete((ok, err) -> {
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (!player.isOnline()) return;
                                if (ok != null && ok) {
                                    String maskedEmail = VerificationDialogFactory.maskEmail(email);
                                    player.sendMessage(config.parse(
                                            "<color:#4AEAFF>Doğrulama kodu " + maskedEmail + " adresine gönderildi.</color>"));
                                    player.sendMessage(config.parse(
                                            "<color:#B0C4D4>Kodu girmek için: <color:#4AEAFF><bold>/dogrula <kod></bold></color></color>"));
                                } else {
                                    vm.removePendingVerification(player.getUniqueId());
                                    player.sendMessage(config.parse(
                                            "<color:#FF6B6B>E-posta gönderilemedi! Lütfen daha sonra tekrar deneyiniz.</color>"));
                                }
                            });
                        });
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
                    case PASSWORD_INVALID -> player.sendMessage(config.msgComponent("register.invalid"));
                    case ALREADY_REGISTERED -> player.sendMessage(config.msgComponent("register.already"));
                    case EMAIL_INVALID -> player.sendMessage(config.msgComponent("register.email_invalid"));
                    case EMAIL_ALREADY_USED -> player.sendMessage(config.msgComponent("register.email_already_used"));
                    case IP_LIMIT -> player.sendMessage(config.parse(
                            "<color:#FF6B6B>Bu IP adresinden maksimum hesap sayısına ulaşıldı!</color>"));
                    default -> player.sendMessage(config.msgComponent("register.failed"));
                }
            });
        });
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
