package com.kauth;

import com.kauth.auth.AuthService;
import com.kauth.command.*;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.listener.JoinListener;
import com.kauth.listener.PlayerProtectionListener;
import com.kauth.storage.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class KAuth extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private AuthService authService;
    private DialogProvider dialogProvider;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        databaseManager = new DatabaseManager(this);
        authService = new AuthService(this, databaseManager);
        dialogProvider = new DialogProvider(this, configManager);

        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new JoinListener(this, authService, configManager, dialogProvider), this);
        pluginManager.registerEvents(new PlayerProtectionListener(this, authService), this);

        var kauthCmd = getCommand("kauth");
        if (kauthCmd != null) {
            var cmd = new KAuthCommand(configManager, authService, dialogProvider);
            kauthCmd.setExecutor(cmd);
            kauthCmd.setTabCompleter(cmd);
        }

        var girisCmd = getCommand("giris");
        if (girisCmd != null) girisCmd.setExecutor(new LoginCommand(this, authService, configManager));

        var kayitCmd = getCommand("kayit");
        if (kayitCmd != null) kayitCmd.setExecutor(new RegisterCommand(this, authService, configManager));

        var cikisCmd = getCommand("cikis");
        if (cikisCmd != null) cikisCmd.setExecutor(new LogoutCommand(this, authService, configManager, dialogProvider));

        var sifreCmd = getCommand("sifredegistir");
        if (sifreCmd != null) sifreCmd.setExecutor(new ChangePasswordCommand(this, authService, configManager));

        getLogger().info("============================kAuth============================");
        getLogger().info("KEYDAL Network - Giriş Sistemi v1.0.0");
        getLogger().info("Mod: " + (dialogProvider.isDialogAvailable() ? "Dialog GUI" : "Chat Tabanlı"));
        getLogger().info("Şifreleme: PBKDF2-SHA256 (65536 iterasyon)");
        getLogger().info("kAuth aktif!");
        getLogger().info("============================kAuth============================");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) databaseManager.close();
        getLogger().info("kAuth devre dışı bırakıldı.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AuthService getAuthService() { return authService; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public DialogProvider getDialogProvider() { return dialogProvider; }
}
