package com.kauth.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class Settings {

    public record Auth(
            boolean blockChat,
            boolean blockCommands,
            boolean freezePlayer,
            boolean blindEffect,
            Set<String> allowedCommands,
            int loginTimeout,
            int maxLoginAttempts,
            int maxIpAttempts,
            int ipBlockMinutes,
            int maxAccountsPerIp,
            int sessionTimeoutMinutes,
            int minPasswordLength,
            int maxPasswordLength,
            boolean blockWeakPasswords,
            boolean requirePasswordConfirmation,
            boolean ipBanOnPreLogin,
            boolean showLastLogin
    ) {
        public Auth {
            allowedCommands = Set.copyOf(allowedCommands);
            if (minPasswordLength < 1) minPasswordLength = 1;
            if (maxPasswordLength < minPasswordLength) maxPasswordLength = minPasswordLength;
            if (loginTimeout < 0) loginTimeout = 0;
            if (maxLoginAttempts < 0) maxLoginAttempts = 0;
            if (maxIpAttempts < 0) maxIpAttempts = 0;
            if (ipBlockMinutes < 0) ipBlockMinutes = 0;
            if (maxAccountsPerIp < 0) maxAccountsPerIp = 0;
            if (sessionTimeoutMinutes < 0) sessionTimeoutMinutes = 0;
        }

        public boolean isCommandAllowed(String rawMessage) {
            if (rawMessage == null || rawMessage.isEmpty()) return false;
            String lower = rawMessage.toLowerCase();
            int space = lower.indexOf(' ');
            String cmd = space == -1 ? lower : lower.substring(0, space);
            if (allowedCommands.contains(cmd)) return true;
            for (String allowed : allowedCommands) {
                if (lower.startsWith(allowed + " ") || lower.equals(allowed)) return true;
            }
            return false;
        }
    }

    public record Email(
            boolean enabled,
            boolean verificationRequired,
            boolean inputEnabled,
            int codeLength,
            int codeExpiryMinutes
    ) {
        public Email {
            if (codeLength < 4) codeLength = 4;
            if (codeLength > 9) codeLength = 9;
            if (codeExpiryMinutes < 1) codeExpiryMinutes = 1;
        }
    }

    public record Velocity(boolean enabled, byte[] secret) {
        public Velocity {
            secret = secret == null ? new byte[0] : secret.clone();
        }

        public byte[] secret() {
            return secret.clone();
        }

        public boolean hasSecret() {
            return secret.length > 0;
        }
    }

    public record Snapshot(Auth auth, Email email, Velocity velocity, boolean loggingEnabled) {}

    private final JavaPlugin plugin;
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public Settings(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public Snapshot get() {
        return snapshot.get();
    }

    public void reload() {
        snapshot.set(loadFromConfig());
    }

    private Snapshot loadFromConfig() {
        FileConfiguration c = plugin.getConfig();

        List<String> cmds = c.getStringList("auth.allowed-commands");
        Set<String> allowedSet = new HashSet<>(cmds.size() * 2);
        for (String s : cmds) {
            if (s != null && !s.isEmpty()) allowedSet.add(s.toLowerCase());
        }

        int minLen = c.getInt("auth.min-password-length", 4);
        int maxLen = c.getInt("auth.max-password-length", 30);
        if (minLen > maxLen) {
            plugin.getLogger().warning("[Config] min-password-length > max-password-length, swap ediliyor");
            int tmp = minLen;
            minLen = maxLen;
            maxLen = tmp;
        }
        int maxAttempts = c.getInt("auth.max-login-attempts", 3);
        if (maxAttempts == 0) {
            plugin.getLogger().warning("[Config] max-login-attempts=0 sınırsız anlamına gelir");
        }

        Auth auth = new Auth(
                c.getBoolean("auth.block-chat", true),
                c.getBoolean("auth.block-commands", true),
                c.getBoolean("auth.freeze-player", true),
                c.getBoolean("auth.blind-effect", true),
                allowedSet,
                c.getInt("auth.login-timeout", 60),
                maxAttempts,
                c.getInt("auth.max-ip-attempts", 20),
                c.getInt("auth.ip-block-minutes", 10),
                c.getInt("auth.max-accounts-per-ip", 3),
                c.getInt("auth.session-timeout", 30),
                minLen,
                maxLen,
                c.getBoolean("auth.block-weak-passwords", true),
                c.getBoolean("auth.require-password-confirmation", true),
                c.getBoolean("auth.ip-ban-on-prelogin", true),
                c.getBoolean("auth.show-last-login", true)
        );

        Email email = new Email(
                c.getBoolean("email.enabled", false),
                c.getBoolean("email.verification.required-on-register", true),
                c.getBoolean("register.email_input.enabled", false),
                c.getInt("email.verification.code-length", 6),
                c.getInt("email.verification.code-expiry-minutes", 10)
        );

        boolean velocityEnabled = c.getBoolean("velocity.enabled", false);
        String secretStr = c.getString("velocity.secret", "");
        byte[] secretBytes = secretStr.isEmpty()
                ? new byte[0]
                : secretStr.getBytes(StandardCharsets.UTF_8);

        if (velocityEnabled && secretBytes.length == 0) {
            plugin.getLogger().severe("[Güvenlik] velocity.enabled=true ama velocity.secret boş, sync devre dışı");
            velocityEnabled = false;
        }

        Velocity velocity = new Velocity(velocityEnabled, secretBytes);
        boolean loggingEnabled = c.getBoolean("logging.enabled", true);

        return new Snapshot(auth, email, velocity, loggingEnabled);
    }
}
