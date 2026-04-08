package com.kauth.email;

import org.bukkit.plugin.Plugin;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VerificationManager {

    private final Plugin plugin;
    private final Map<UUID, VerificationEntry> pendingVerifications = new ConcurrentHashMap<>();
    private final Map<String, ResetEntry> pendingResets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEmailSent = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public record VerificationEntry(String email, String code, long expiresAt) {}
    public record ResetEntry(String email, String code, long expiresAt, String username) {}

    public VerificationManager(Plugin plugin) {
        this.plugin = plugin;

        // Süresi dolmuş kodları temizle (her 2 dakika)
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            pendingVerifications.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            pendingResets.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            lastEmailSent.entrySet().removeIf(e -> now - e.getValue() > 120_000);
        }, 2400L, 2400L);
    }

    // ===== E-posta Doğrulama (Kayıt) =====

    public String generateVerificationCode(UUID playerUuid, String email) {
        String code = generateCode();
        long expiry = System.currentTimeMillis() + getExpiryMillis();
        pendingVerifications.put(playerUuid, new VerificationEntry(email, code, expiry));
        lastEmailSent.put(playerUuid, System.currentTimeMillis());
        return code;
    }

    public boolean verifyEmail(UUID playerUuid, String inputCode) {
        VerificationEntry entry = pendingVerifications.get(playerUuid);
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            pendingVerifications.remove(playerUuid);
            return false;
        }
        if (entry.code().equals(inputCode)) {
            pendingVerifications.remove(playerUuid);
            return true;
        }
        return false;
    }

    public boolean hasPendingVerification(UUID playerUuid) {
        VerificationEntry entry = pendingVerifications.get(playerUuid);
        return entry != null && System.currentTimeMillis() <= entry.expiresAt();
    }

    public VerificationEntry getPendingVerification(UUID playerUuid) {
        return pendingVerifications.get(playerUuid);
    }

    public void removePendingVerification(UUID playerUuid) {
        pendingVerifications.remove(playerUuid);
    }

    // ===== Şifre Sıfırlama =====

    public String generateResetCode(String username, String email) {
        String code = generateCode();
        long expiry = System.currentTimeMillis() + getExpiryMillis();
        pendingResets.put(username.toLowerCase(), new ResetEntry(email, code, expiry, username));
        return code;
    }

    public boolean verifyReset(String username, String inputCode) {
        ResetEntry entry = pendingResets.get(username.toLowerCase());
        if (entry == null) return false;
        if (System.currentTimeMillis() > entry.expiresAt()) {
            pendingResets.remove(username.toLowerCase());
            return false;
        }
        if (entry.code().equals(inputCode)) {
            pendingResets.remove(username.toLowerCase());
            return true;
        }
        return false;
    }

    public boolean hasPendingReset(String username) {
        ResetEntry entry = pendingResets.get(username.toLowerCase());
        return entry != null && System.currentTimeMillis() <= entry.expiresAt();
    }

    public ResetEntry getPendingReset(String username) {
        return pendingResets.get(username.toLowerCase());
    }

    // ===== Rate Limiting =====

    public boolean canSendEmail(UUID playerUuid) {
        Long last = lastEmailSent.get(playerUuid);
        if (last == null) return true;
        return System.currentTimeMillis() - last > 60_000; // 1 dakikada 1 e-posta
    }

    // ===== Helper =====

    private String generateCode() {
        int length = plugin.getConfig().getInt("email.verification.code-length", 6);
        int max = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", RANDOM.nextInt(max));
    }

    private long getExpiryMillis() {
        return plugin.getConfig().getInt("email.verification.code-expiry-minutes", 10) * 60_000L;
    }
}
