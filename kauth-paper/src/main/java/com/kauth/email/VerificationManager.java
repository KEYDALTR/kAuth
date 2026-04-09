package com.kauth.email;

import com.kauth.KAuth;
import com.kauth.config.Settings;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class VerificationManager {

    public static final int MAX_VERIFY_ATTEMPTS = 3;
    public static final long TARGET_RATE_LIMIT_MS = 60_000L;
    public static final long REQUESTER_RATE_LIMIT_MS = 60_000L;

    private final KAuth plugin;
    private final Map<UUID, VerificationEntry> pendingVerifications = new ConcurrentHashMap<>();
    private final Map<String, ResetEntry> pendingResets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEmailSent = new ConcurrentHashMap<>();
    private final Map<String, Long> lastResetByTarget = new ConcurrentHashMap<>();
    private static final SecureRandom RANDOM = new SecureRandom();

    public record VerificationEntry(String email, String code, long expiresAt, AtomicInteger attempts) {
        public VerificationEntry(String email, String code, long expiresAt) {
            this(email, code, expiresAt, new AtomicInteger(0));
        }
    }

    public record ResetEntry(String email, String code, long expiresAt, String username, AtomicInteger attempts) {
        public ResetEntry(String email, String code, long expiresAt, String username) {
            this(email, code, expiresAt, username, new AtomicInteger(0));
        }
    }

    public VerificationManager(KAuth plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            pendingVerifications.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            pendingResets.entrySet().removeIf(e -> e.getValue().expiresAt() < now);
            lastEmailSent.entrySet().removeIf(e -> now - e.getValue() > 300_000);
            lastResetByTarget.entrySet().removeIf(e -> now - e.getValue() > 300_000);
        }, 2400L, 2400L);
    }

    private Settings.Email emailSettings() {
        return plugin.getConfigManager().getSettings().get().email();
    }

    public String generateVerificationCode(UUID playerUuid, String email) {
        String code = generateCode();
        long expiry = System.currentTimeMillis() + getExpiryMillis();
        pendingVerifications.put(playerUuid, new VerificationEntry(email, code, expiry));
        lastEmailSent.put(playerUuid, System.currentTimeMillis());
        return code;
    }

    public VerifyResult verifyEmail(UUID playerUuid, String inputCode) {
        VerifyResult[] holder = new VerifyResult[]{VerifyResult.NO_PENDING};
        pendingVerifications.compute(playerUuid, (k, entry) -> {
            if (entry == null) { holder[0] = VerifyResult.NO_PENDING; return null; }
            if (System.currentTimeMillis() > entry.expiresAt()) {
                holder[0] = VerifyResult.EXPIRED;
                return null;
            }
            if (entry.code().equals(inputCode)) {
                holder[0] = VerifyResult.OK;
                return null;
            }
            int n = entry.attempts().incrementAndGet();
            if (n >= MAX_VERIFY_ATTEMPTS) {
                holder[0] = VerifyResult.TOO_MANY_ATTEMPTS;
                return null;
            }
            holder[0] = VerifyResult.WRONG;
            return entry;
        });
        return holder[0];
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

    public String generateResetCode(String username, String email) {
        String code = generateCode();
        long expiry = System.currentTimeMillis() + getExpiryMillis();
        pendingResets.put(username.toLowerCase(), new ResetEntry(email, code, expiry, username));
        lastResetByTarget.put(username.toLowerCase(), System.currentTimeMillis());
        return code;
    }

    public VerifyResult verifyReset(String username, String inputCode) {
        VerifyResult[] holder = new VerifyResult[]{VerifyResult.NO_PENDING};
        pendingResets.compute(username.toLowerCase(), (k, entry) -> {
            if (entry == null) { holder[0] = VerifyResult.NO_PENDING; return null; }
            if (System.currentTimeMillis() > entry.expiresAt()) {
                holder[0] = VerifyResult.EXPIRED;
                return null;
            }
            if (entry.code().equals(inputCode)) {
                holder[0] = VerifyResult.OK;
                return null;
            }
            int n = entry.attempts().incrementAndGet();
            if (n >= MAX_VERIFY_ATTEMPTS) {
                holder[0] = VerifyResult.TOO_MANY_ATTEMPTS;
                return null;
            }
            holder[0] = VerifyResult.WRONG;
            return entry;
        });
        return holder[0];
    }

    public boolean hasPendingReset(String username) {
        ResetEntry entry = pendingResets.get(username.toLowerCase());
        return entry != null && System.currentTimeMillis() <= entry.expiresAt();
    }

    public ResetEntry getPendingReset(String username) {
        return pendingResets.get(username.toLowerCase());
    }

    public boolean canSendEmail(UUID playerUuid) {
        Long last = lastEmailSent.get(playerUuid);
        if (last == null) return true;
        return System.currentTimeMillis() - last > REQUESTER_RATE_LIMIT_MS;
    }

    public boolean canRequestResetForTarget(String targetUsername) {
        Long last = lastResetByTarget.get(targetUsername.toLowerCase());
        if (last == null) return true;
        return System.currentTimeMillis() - last > TARGET_RATE_LIMIT_MS;
    }

    private String generateCode() {
        int length = emailSettings().codeLength();
        int max = (int) Math.pow(10, length);
        return String.format("%0" + length + "d", RANDOM.nextInt(max));
    }

    private long getExpiryMillis() {
        return emailSettings().codeExpiryMinutes() * 60_000L;
    }

    public enum VerifyResult {
        OK, WRONG, EXPIRED, NO_PENDING, TOO_MANY_ATTEMPTS
    }
}
