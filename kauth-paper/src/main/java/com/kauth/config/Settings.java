package com.kauth.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Hot-path config değerlerini cache'ler.
 * Her event'te config.getXxx() çağrısı yerine bu sınıftan okunur.
 * Plugin enable + /kauth reload sonrası reload() çağrılır.
 */
public class Settings {

    private final JavaPlugin plugin;

    // === Auth ===
    public volatile boolean blockChat;
    public volatile boolean blockCommands;
    public volatile boolean freezePlayer;
    public volatile boolean blindEffect;
    public volatile Set<String> allowedCommands = Collections.emptySet();
    public volatile int loginTimeout;
    public volatile int maxLoginAttempts;
    public volatile int maxIpAttempts;
    public volatile int ipBlockMinutes;
    public volatile int maxAccountsPerIp;
    public volatile int sessionTimeoutMinutes;
    public volatile int minPasswordLength;
    public volatile int maxPasswordLength;
    public volatile boolean blockWeakPasswords;
    public volatile boolean requirePasswordConfirmation;
    public volatile boolean ipBanOnPreLogin;
    public volatile boolean showLastLogin;

    // === Logging ===
    public volatile boolean loggingEnabled;

    // === Email ===
    public volatile boolean emailEnabled;
    public volatile boolean emailVerificationRequired;
    public volatile boolean emailInputEnabled;

    // === Velocity ===
    public volatile boolean velocityEnabled;

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        FileConfiguration c = plugin.getConfig();

        // Auth
        blockChat = c.getBoolean("auth.block-chat", true);
        blockCommands = c.getBoolean("auth.block-commands", true);
        freezePlayer = c.getBoolean("auth.freeze-player", true);
        blindEffect = c.getBoolean("auth.blind-effect", true);
        loginTimeout = c.getInt("auth.login-timeout", 60);
        maxLoginAttempts = c.getInt("auth.max-login-attempts", 3);
        maxIpAttempts = c.getInt("auth.max-ip-attempts", 20);
        ipBlockMinutes = c.getInt("auth.ip-block-minutes", 10);
        maxAccountsPerIp = c.getInt("auth.max-accounts-per-ip", 3);
        sessionTimeoutMinutes = c.getInt("auth.session-timeout", 30);
        minPasswordLength = c.getInt("auth.min-password-length", 4);
        maxPasswordLength = c.getInt("auth.max-password-length", 30);
        blockWeakPasswords = c.getBoolean("auth.block-weak-passwords", true);
        requirePasswordConfirmation = c.getBoolean("auth.require-password-confirmation", true);
        ipBanOnPreLogin = c.getBoolean("auth.ip-ban-on-prelogin", true);
        showLastLogin = c.getBoolean("auth.show-last-login", true);

        // Allowed commands → Set (O(1) lookup)
        List<String> cmds = c.getStringList("auth.allowed-commands");
        Set<String> set = new HashSet<>(cmds.size() * 2);
        for (String s : cmds) {
            if (s != null && !s.isEmpty()) {
                set.add(s.toLowerCase());
            }
        }
        allowedCommands = Collections.unmodifiableSet(set);

        // Logging
        loggingEnabled = c.getBoolean("logging.enabled", true);

        // Email
        emailEnabled = c.getBoolean("email.enabled", false);
        emailVerificationRequired = c.getBoolean("email.verification.required-on-register", true);
        emailInputEnabled = c.getBoolean("register.email_input.enabled", false);

        // Velocity
        velocityEnabled = c.getBoolean("velocity.enabled", false);
    }

    /**
     * Komutun allowed list'te olup olmadığını kontrol et (case-insensitive prefix).
     * `/help world` → `/help` match eder.
     */
    public boolean isCommandAllowed(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) return false;
        String lower = rawMessage.toLowerCase();
        // İlk boşluğa kadar olan kısım + tam eşleşme kontrolü
        int space = lower.indexOf(' ');
        String cmd = space == -1 ? lower : lower.substring(0, space);
        if (allowedCommands.contains(cmd)) return true;
        // Prefix kontrolü (eski davranışla uyum)
        for (String allowed : allowedCommands) {
            if (lower.startsWith(allowed + " ") || lower.equals(allowed)) return true;
        }
        return false;
    }
}
