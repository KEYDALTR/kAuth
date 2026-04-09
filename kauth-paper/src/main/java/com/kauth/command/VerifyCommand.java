package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.email.VerificationManager;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.logging.Level;

public class VerifyCommand implements CommandExecutor {

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final VerificationManager verificationManager;
    private final DialogProvider dialogProvider;

    public VerifyCommand(KAuth plugin, AuthService auth, ConfigManager config,
                         VerificationManager verificationManager, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.verificationManager = verificationManager;
        this.dialogProvider = dialogProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.msgComponent("admin.player-only"));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(config.msgComponent("email-verify.usage"));
            return true;
        }

        if (!verificationManager.hasPendingVerification(player.getUniqueId())) {
            player.sendMessage(config.msgComponent("email-verify.no-pending"));
            return true;
        }

        String code = args[0].trim();
        VerificationManager.VerifyResult result = verificationManager.verifyEmail(player.getUniqueId(), code);

        switch (result) {
            case OK -> {
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        auth.getDb().setEmailVerified(player.getName(), true);
                    } catch (DataAccessException e) {
                        plugin.getLogger().log(Level.SEVERE, "[kAuth] Verify DB hatası", e);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (player.isOnline()) {
                                player.kick(config.parse(
                                        "<color:#FF6B6B>Veritabanı hatası, tekrar bağlanınız.</color>"));
                            }
                        });
                        return;
                    }
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) return;
                        auth.forceLogin(player);
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                        dialogProvider.closeDialog(player);
                        player.sendMessage(config.msgComponent("register.success"));
                        player.sendMessage(config.parse("<color:#4AEAFF>E-posta adresiniz doğrulandı!</color>"));
                        EffectUtil.playEffect(plugin, player, "register-success");
                    });
                });
            }
            case WRONG -> player.sendMessage(config.msgComponent("email-verify.invalid-code"));
            case EXPIRED -> player.sendMessage(config.parse("<color:#FF6B6B>Kod süresi doldu, lütfen tekrar bağlanınız.</color>"));
            case NO_PENDING -> player.sendMessage(config.msgComponent("email-verify.no-pending"));
            case TOO_MANY_ATTEMPTS -> player.sendMessage(config.parse(
                    "<color:#FF6B6B>Çok fazla yanlış deneme! Kod iptal edildi, lütfen tekrar bağlanınız.</color>"));
        }

        return true;
    }
}
