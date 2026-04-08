package com.kauth;

import com.kauth.auth.AuthService;
import com.kauth.command.*;
import com.kauth.common.storage.AuthDatabase;
import com.kauth.common.storage.MySQLDatabase;
import com.kauth.common.storage.SQLiteDatabase;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.email.EmailService;
import com.kauth.email.VerificationManager;
import com.kauth.listener.JoinListener;
import com.kauth.listener.PlayerProtectionListener;
import com.kauth.messaging.MessagingHelper;
import com.kauth.messaging.PaperMessageListener;
import com.kauth.common.messaging.MessageConstants;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class KAuth extends JavaPlugin {

    private ConfigManager configManager;
    private AuthDatabase database;
    private AuthService authService;
    private DialogProvider dialogProvider;
    private EmailService emailService;
    private VerificationManager verificationManager;
    private MessagingHelper messagingHelper;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);

        // Veritabanı seçimi
        String dbType = getConfig().getString("database.type", "sqlite");
        if ("mysql".equalsIgnoreCase(dbType)) {
            database = new MySQLDatabase(
                    getConfig().getString("database.mysql.host", "localhost"),
                    getConfig().getInt("database.mysql.port", 3306),
                    getConfig().getString("database.mysql.database", "kauth"),
                    getConfig().getString("database.mysql.username", "root"),
                    getConfig().getString("database.mysql.password", ""),
                    getConfig().getInt("database.mysql.pool-size", 10),
                    getLogger()
            );
        } else {
            database = new SQLiteDatabase(
                    new File(getDataFolder(), "kauth.db"),
                    getLogger()
            );
        }
        database.initialize();

        // E-posta servisi
        emailService = new EmailService(this);
        verificationManager = new VerificationManager(this);

        authService = new AuthService(this, database);
        dialogProvider = new DialogProvider(this, configManager);

        // Velocity messaging
        if (getConfig().getBoolean("velocity.enabled", false)) {
            messagingHelper = new MessagingHelper(this);
            getServer().getMessenger().registerOutgoingPluginChannel(this, MessageConstants.CHANNEL);
            getServer().getMessenger().registerIncomingPluginChannel(this, MessageConstants.CHANNEL,
                    new PaperMessageListener(this));
        }

        // Listener'lar
        var pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new JoinListener(this, authService, configManager, dialogProvider), this);
        pluginManager.registerEvents(new PlayerProtectionListener(this, authService), this);

        // Komutlar
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

        var resetCmd = getCommand("sifresifirla");
        if (resetCmd != null) resetCmd.setExecutor(new PasswordResetCommand(this, authService, configManager, emailService, verificationManager));

        var dogrulaCmd = getCommand("dogrula");
        if (dogrulaCmd != null) dogrulaCmd.setExecutor(new VerifyCommand(this, authService, configManager, verificationManager, dialogProvider));

        getLogger().info("============================kAuth============================");
        getLogger().info("KEYDAL Network - Giriş Sistemi v1.0.1");
        getLogger().info("Mod: " + (dialogProvider.isDialogAvailable() ? "Dialog GUI" : "Chat Tabanlı"));
        getLogger().info("Veritabanı: " + dbType.toUpperCase());
        getLogger().info("E-posta: " + (emailService.isEnabled() ? "Aktif (" + getConfig().getString("email.smtp.host", "") + ")" : "Devre dışı"));
        getLogger().info("Velocity: " + (getConfig().getBoolean("velocity.enabled", false) ? "Aktif" : "Devre dışı"));
        getLogger().info("Şifreleme: PBKDF2-SHA256 (65536 iterasyon)");
        getLogger().info("kAuth aktif!");
        getLogger().info("============================kAuth============================");
    }

    @Override
    public void onDisable() {
        if (database != null) database.close();
        if (messagingHelper != null) {
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, MessageConstants.CHANNEL);
            getServer().getMessenger().unregisterIncomingPluginChannel(this, MessageConstants.CHANNEL);
        }
        getLogger().info("kAuth devre dışı bırakıldı.");
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AuthService getAuthService() { return authService; }
    public AuthDatabase getDatabase() { return database; }
    public DialogProvider getDialogProvider() { return dialogProvider; }
    public EmailService getEmailService() { return emailService; }
    public VerificationManager getVerificationManager() { return verificationManager; }
    public MessagingHelper getMessagingHelper() { return messagingHelper; }
}
