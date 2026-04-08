package com.kauth.command;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.email.VerificationManager;
import com.kauth.util.EffectUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

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

        if (verificationManager.verifyEmail(player.getUniqueId(), code)) {
            // Doğrulama başarılı
            auth.getDb().setEmailVerified(player.getName(), true);
            auth.forceLogin(player);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            dialogProvider.closeDialog(player);
            player.sendMessage(config.msgComponent("register.success"));
            player.sendMessage(config.parse("<color:#4AEAFF>E-posta adresiniz doğrulandı!</color>"));
            EffectUtil.playEffect(plugin, player, "register-success");
        } else {
            player.sendMessage(config.msgComponent("email-verify.invalid-code"));
        }

        return true;
    }
}
