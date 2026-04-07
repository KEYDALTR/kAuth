package com.kauth.dialog;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Client sürümüne göre otomatik mod seçimi:
 * - 1.21.5+ client (protocol >= 770) → Dialog GUI
 * - Eski client → Chat mesajları + /giris, /kayit komutları
 *
 * Sunucu 1.21.11'de çalışır, ViaVersion ile eski clientlar bağlanır.
 */
public class DialogProvider {

    private final KAuth plugin;
    private final ConfigManager config;
    private final boolean serverHasDialogApi;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Minecraft 1.21.5 = protocol 770 (Dialog desteği başladığı sürüm)
    private static final int DIALOG_MIN_PROTOCOL = 770;

    private LoginDialogFactory loginFactory;
    private RegisterDialogFactory registerFactory;
    private RuleDialogFactory ruleFactory;

    public DialogProvider(KAuth plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.serverHasDialogApi = checkDialogApi();

        if (serverHasDialogApi) {
            loginFactory = new LoginDialogFactory(plugin, config);
            registerFactory = new RegisterDialogFactory(plugin, config);
            ruleFactory = new RuleDialogFactory(plugin, config);
            plugin.getLogger().info("Dialog API mevcut - client sürümüne göre otomatik mod");
        } else {
            plugin.getLogger().info("Dialog API bulunamadı - tüm oyuncular chat moduyla giriş yapacak");
        }
    }

    private boolean checkDialogApi() {
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Oyuncunun client sürümü Dialog destekliyor mu?
     */
    public boolean supportsDialog(Player player) {
        if (!serverHasDialogApi) return false;
        try {
            int protocol = player.getProtocolVersion();
            return protocol >= DIALOG_MIN_PROTOCOL;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isDialogAvailable() {
        return serverHasDialogApi;
    }

    public String getPlayerMode(Player player) {
        return supportsDialog(player) ? "Dialog GUI" : "Chat";
    }

    // ===== Login =====

    public void showLogin(Player player) {
        if (supportsDialog(player)) {
            try { player.showDialog(loginFactory.build(player)); } catch (Exception ignored) {}
        } else {
            sendChatLogin(player, null);
        }
    }

    public void showLogin(Player player, net.kyori.adventure.text.Component errorMsg) {
        if (supportsDialog(player)) {
            try { player.showDialog(loginFactory.build(player, errorMsg)); } catch (Exception ignored) {}
        } else {
            sendChatLogin(player, errorMsg);
        }
    }

    // ===== Register =====

    public void showRegister(Player player) {
        if (supportsDialog(player)) {
            try { player.showDialog(registerFactory.build(player)); } catch (Exception ignored) {}
        } else {
            sendChatRegister(player, null);
        }
    }

    public void showRegister(Player player, net.kyori.adventure.text.Component errorMsg) {
        if (supportsDialog(player)) {
            try { player.showDialog(registerFactory.build(player, errorMsg)); } catch (Exception ignored) {}
        } else {
            sendChatRegister(player, errorMsg);
        }
    }

    // ===== Rules =====

    public void showRules(Player player) {
        if (supportsDialog(player)) {
            try { player.showDialog(ruleFactory.build(player)); } catch (Exception ignored) {}
        } else {
            sendChatRules(player);
        }
    }

    // ===== Close =====

    public void closeDialog(Player player) {
        if (supportsDialog(player)) {
            try { player.closeDialog(); } catch (Exception ignored) {}
        }
    }

    // ===== Chat Fallback =====

    private void sendChatLogin(Player player, net.kyori.adventure.text.Component error) {
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("  <color:#00D4FF><bold>KEYDAL</bold></color> <color:#3A4F6A>│</color> <color:#4AEAFF>Giriş Yap</color>"));
        player.sendMessage(mm.deserialize(""));
        if (error != null) {
            player.sendMessage(mm.deserialize("  <color:#FF6B6B>!</color> ").append(error));
            player.sendMessage(mm.deserialize(""));
        }
        player.sendMessage(mm.deserialize("  <color:#B0C4D4>Şifrenizi girmek için yazın:</color>"));
        player.sendMessage(mm.deserialize("  <color:#4AEAFF><bold>/giris <şifreniz></bold></color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
    }

    private void sendChatRegister(Player player, net.kyori.adventure.text.Component error) {
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("  <color:#00D4FF><bold>KEYDAL</bold></color> <color:#3A4F6A>│</color> <color:#C8E0FF>Kayıt Ol</color>"));
        player.sendMessage(mm.deserialize(""));
        if (error != null) {
            player.sendMessage(mm.deserialize("  <color:#FF6B6B>!</color> ").append(error));
            player.sendMessage(mm.deserialize(""));
        }
        player.sendMessage(mm.deserialize("  <color:#B0C4D4>Hesap oluşturmak için yazın:</color>"));
        player.sendMessage(mm.deserialize("  <color:#4AEAFF><bold>/kayit <şifre> <şifre tekrar></bold></color>"));
        player.sendMessage(mm.deserialize(""));
        int min = plugin.getConfig().getInt("auth.min-password-length", 4);
        player.sendMessage(mm.deserialize("  <color:#3A4F6A>Minimum " + min + " karakter</color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
    }

    private void sendChatRules(Player player) {
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("  <color:#00D4FF><bold>KEYDAL</bold></color> <color:#3A4F6A>│</color> <color:#C8E0FF>Sunucu Kuralları</color>"));
        player.sendMessage(mm.deserialize(""));
        for (String line : config.ruleBodyRaw()) {
            player.sendMessage(mm.deserialize("  " + line));
        }
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("  <color:#B0C4D4>Kuralları kabul edip kayıt olmak için:</color>"));
        player.sendMessage(mm.deserialize("  <color:#4AEAFF><bold>/kayit <şifre> <şifre tekrar></bold></color>"));
        player.sendMessage(mm.deserialize(""));
        player.sendMessage(mm.deserialize("<color:#1A2B6B><st>                                                            </st></color>"));
        player.sendMessage(mm.deserialize(""));
    }

    public LoginDialogFactory getLoginFactory() { return loginFactory; }
    public RegisterDialogFactory getRegisterFactory() { return registerFactory; }
    public RuleDialogFactory getRuleFactory() { return ruleFactory; }
}
