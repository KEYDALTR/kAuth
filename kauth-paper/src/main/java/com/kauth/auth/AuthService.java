package com.kauth.auth;

import com.kauth.KAuth;
import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.kauth.common.storage.AuthDatabase;
import com.kauth.common.storage.DataAccessException;
import com.kauth.common.storage.PasswordUtil;
import com.kauth.config.Settings;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;

public class AuthService {

    private record SessionEntry(long loginTime, String ip, String playerName) {
        public SessionEntry {
            if (ip == null || ip.isEmpty()) throw new IllegalArgumentException("ip null/empty");
            if (playerName == null || playerName.isEmpty()) throw new IllegalArgumentException("name null/empty");
        }
        public boolean isValid(long now, long timeoutMs, String currentIp) {
            if (!ip.equals(currentIp)) return false;
            return (now - loginTime) < timeoutMs;
        }
    }

    private final KAuth plugin;
    private final AuthDatabase db;
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, AtomicInteger> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Map<String, UUID> sessionsByName = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBlockedUntil = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> authSuccessHandlers = new CopyOnWriteArrayList<>();

    public AuthService(KAuth plugin, AuthDatabase db) {
        this.plugin = plugin;
        this.db = db;

        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            ipBlockedUntil.entrySet().removeIf(e -> e.getValue() < now);
            ipAttempts.entrySet().removeIf(e -> {
                Long blockedUntil = ipBlockedUntil.get(e.getKey());
                return blockedUntil == null && e.getValue().get() < settings().auth().maxIpAttempts();
            });
        }, 6000L, 6000L);
    }

    private Settings.Snapshot settings() {
        return plugin.getConfigManager().getSettings().get();
    }

    public boolean isRegistered(String playerName) throws DataAccessException {
        return db.isRegistered(playerName);
    }

    public boolean isAuthenticated(Player player) {
        return authenticatedPlayers.contains(player.getUniqueId());
    }

    public void setAuthenticated(Player player, boolean authenticated) throws DataAccessException {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Settings.Snapshot s = settings();

        if (authenticated) {
            authenticatedPlayers.add(uuid);
            loginAttempts.remove(uuid);
            String ip = getIpOrNull(player);
            if (ip != null) {
                ipAttempts.remove(ip);
                db.updateLastLogin(name, ip);
                int sessionTimeout = s.auth().sessionTimeoutMinutes();
                if (sessionTimeout > 0) {
                    SessionEntry entry = new SessionEntry(System.currentTimeMillis(), ip, name);
                    sessions.put(uuid, entry);
                    sessionsByName.put(name.toLowerCase(), uuid);
                }
            } else {
                plugin.getLogger().warning("[kAuth] setAuthenticated: player.getAddress() null: " + name);
                db.updateLastLogin(name, "unknown");
            }

            runOnMainThread(() -> {
                sendSyncMessage(player, MessageConstants.LOGIN);
                fireAuthSuccess(player);
            });
        } else {
            authenticatedPlayers.remove(uuid);
            sessions.remove(uuid);
            sessionsByName.remove(name.toLowerCase());
            db.setLoggedIn(name, false);
            runOnMainThread(() -> sendSyncMessage(player, MessageConstants.LOGOUT));
        }
    }

    public void forceLoginRemote(Player player) {
        authenticatedPlayers.add(player.getUniqueId());
        loginAttempts.remove(player.getUniqueId());
        runOnMainThread(() -> fireAuthSuccess(player));
    }

    public void registerAuthSuccessHandler(Consumer<Player> handler) {
        authSuccessHandlers.add(handler);
    }

    private void fireAuthSuccess(Player player) {
        for (Consumer<Player> h : authSuccessHandlers) {
            try {
                h.accept(player);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "[kAuth] AuthSuccess handler hata verdi (" + player.getName() + "): " + e.getMessage(), e);
            }
        }
    }

    private void runOnMainThread(Runnable r) {
        if (plugin.getServer().isPrimaryThread()) {
            r.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, r);
        }
    }

    public boolean hasValidSession(Player player) {
        int sessionTimeout = settings().auth().sessionTimeoutMinutes();
        if (sessionTimeout <= 0) return false;
        String currentIp = getIpOrNull(player);
        if (currentIp == null) return false;
        SessionEntry entry = sessions.get(player.getUniqueId());
        if (entry == null) return false;
        return entry.isValid(System.currentTimeMillis(), sessionTimeout * 60_000L, currentIp);
    }

    public void clearSession(UUID uuid) {
        SessionEntry entry = sessions.remove(uuid);
        if (entry != null) {
            sessionsByName.remove(entry.playerName().toLowerCase());
        }
    }

    public void clearSession(String playerName) {
        UUID uuid = sessionsByName.remove(playerName.toLowerCase());
        if (uuid != null) {
            sessions.remove(uuid);
        }
    }

    public boolean isIpBlocked(Player player) {
        String ip = getIpOrNull(player);
        if (ip == null) return false;
        return isIpBlocked(ip);
    }

    public boolean isIpBlocked(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        Long blockedUntil = ipBlockedUntil.get(ip);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) return true;
        int maxIpAttempts = settings().auth().maxIpAttempts();
        if (maxIpAttempts <= 0) return false;
        AtomicInteger counter = ipAttempts.get(ip);
        return counter != null && counter.get() >= maxIpAttempts;
    }

    public long getIpBlockedUntil(String ip) {
        Long until = ipBlockedUntil.get(ip);
        return until == null ? 0 : until;
    }

    public long getIpBlockRemainingSeconds(String ip) {
        long until = getIpBlockedUntil(ip);
        if (until == 0) return 0;
        return Math.max(0, (until - System.currentTimeMillis()) / 1000);
    }

    public void incrementIpAttempts(Player player) {
        String ip = getIpOrNull(player);
        if (ip == null) return;
        int current = ipAttempts.computeIfAbsent(ip, k -> new AtomicInteger(0)).incrementAndGet();
        Settings.Auth auth = settings().auth();
        if (auth.maxIpAttempts() > 0 && current >= auth.maxIpAttempts()) {
            ipBlockedUntil.put(ip, System.currentTimeMillis() + auth.ipBlockMinutes() * 60_000L);
            plugin.getLogger().warning("[Güvenlik] IP engellendi: " + ip + " (" + auth.ipBlockMinutes() + " dk)");
        }
    }

    public boolean canRegisterFromIp(Player player) throws DataAccessException {
        int maxPerIp = settings().auth().maxAccountsPerIp();
        if (maxPerIp <= 0) return true;
        String ip = getIpOrNull(player);
        if (ip == null) return false;
        return db.getAccountCountByIp(ip) < maxPerIp;
    }

    public void removePlayer(UUID uuid) {
        authenticatedPlayers.remove(uuid);
        loginAttempts.remove(uuid);
    }

    public boolean checkPassword(String playerName, String password) throws DataAccessException {
        String storedHash = db.getHashedPassword(playerName);
        if (storedHash == null) return false;
        return PasswordUtil.verify(password, storedHash);
    }

    public void forceLogin(Player player) {
        try {
            setAuthenticated(player, true);
        } catch (DataAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "[kAuth] forceLogin DB hatası (" + player.getName() + ")", e);
        }
    }

    public int incrementAttempts(Player player) {
        int current = loginAttempts.computeIfAbsent(player.getUniqueId(), k -> new AtomicInteger(0)).incrementAndGet();
        incrementIpAttempts(player);
        return current;
    }

    public int getMaxAttempts() {
        return settings().auth().maxLoginAttempts();
    }

    public int getRemainingAttempts(Player player) {
        int max = getMaxAttempts();
        if (max <= 0) return -1;
        AtomicInteger counter = loginAttempts.get(player.getUniqueId());
        int used = counter == null ? 0 : counter.get();
        return Math.max(0, max - used);
    }

    public int getUsedAttempts(Player player) {
        AtomicInteger counter = loginAttempts.get(player.getUniqueId());
        return counter == null ? 0 : counter.get();
    }

    public PasswordValidationResult validatePassword(String playerName, String password) {
        Settings.Auth a = settings().auth();
        if (password == null || password.isEmpty()) return PasswordValidationResult.TOO_SHORT;
        if (password.length() < a.minPasswordLength()) return PasswordValidationResult.TOO_SHORT;
        if (password.length() > a.maxPasswordLength()) return PasswordValidationResult.TOO_LONG;
        if (isWeakPassword(password, playerName)) return PasswordValidationResult.WEAK;
        return PasswordValidationResult.OK;
    }

    public enum PasswordValidationResult { OK, TOO_SHORT, TOO_LONG, WEAK }

    public RegisterResult register(Player player, String password, String confirmPassword) throws DataAccessException {
        return register(player, password, confirmPassword, null);
    }

    public RegisterResult register(Player player, String password, String confirmPassword, String email) throws DataAccessException {
        if (db.isRegistered(player.getName())) return RegisterResult.ALREADY_REGISTERED;
        Settings.Snapshot s = settings();
        if (s.auth().requirePasswordConfirmation() && !password.equals(confirmPassword)) {
            return RegisterResult.PASSWORD_MISMATCH;
        }

        PasswordValidationResult pv = validatePassword(player.getName(), password);
        switch (pv) {
            case TOO_SHORT: return RegisterResult.PASSWORD_TOO_SHORT;
            case TOO_LONG:  return RegisterResult.PASSWORD_TOO_LONG;
            case WEAK:      return RegisterResult.PASSWORD_INVALID;
            case OK:        break;
        }

        if (!canRegisterFromIp(player)) return RegisterResult.IP_LIMIT;

        if (email != null && !email.isEmpty()) {
            if (!isValidEmail(email)) return RegisterResult.EMAIL_INVALID;
            if (db.isEmailUsed(email)) return RegisterResult.EMAIL_ALREADY_USED;
        }

        String hashedPassword = PasswordUtil.hash(password);
        String ip = getIpOrNull(player);
        if (ip == null) ip = "unknown";

        boolean success;
        if (email != null && !email.isEmpty()) {
            success = db.registerWithEmail(player.getName(), player.getUniqueId().toString(), hashedPassword, ip, email);
        } else {
            success = db.register(player.getName(), player.getUniqueId().toString(), hashedPassword, ip);
        }

        if (!success) return RegisterResult.FAILED;

        boolean emailVerificationRequired = s.email().enabled()
                && s.email().verificationRequired()
                && email != null && !email.isEmpty();
        if (emailVerificationRequired) return RegisterResult.PENDING_EMAIL_VERIFICATION;

        setAuthenticated(player, true);
        return RegisterResult.SUCCESS;
    }

    static final Set<String> WEAK_PASSWORDS = Set.of(
            "1234", "12345", "123456", "1234567", "12345678", "123456789", "1234567890",
            "0000", "00000", "000000", "1111", "11111", "111111", "2222", "22222", "222222",
            "9999", "99999", "999999", "1212", "121212", "123123", "654321",
            "qwerty", "qwertyuiop", "qwerty123", "asdfgh", "asdfghjkl", "zxcvbn", "zxcvbnm",
            "qwer", "asdf", "zxcv", "qweasd", "1qaz2wsx", "qazwsx",
            "password", "password1", "password123", "admin", "administrator", "root",
            "letmein", "welcome", "login", "master", "dragon", "monkey", "shadow",
            "football", "baseball", "iloveyou", "sunshine", "princess", "superman",
            "batman", "trustno1", "starwars", "freedom", "whatever",
            "sifre", "sifre123", "parola", "giris", "kullanici", "sunucu", "oyun",
            "fenerbahce", "galatasaray", "besiktas", "trabzon", "ataturk", "turkiye",
            "sevgilim", "canim", "askim", "benim", "seninle", "kimse",
            "minecraft", "minecraft1", "minecraft123", "steve", "herobrine", "notch",
            "creeper", "enderman", "diamond", "obsidian",
            "abc", "abcd", "abcde", "abcdef", "abcdefg", "abcdefgh", "abcdefghi",
            "abc123", "aaa", "aaaa", "aaaaa"
    );

    boolean isWeakPassword(String password, String playerName) {
        if (!settings().auth().blockWeakPasswords()) return false;
        if (password == null) return true;
        String lower = password.toLowerCase();

        if (playerName != null) {
            String lowerName = playerName.toLowerCase();
            if (lower.equals(lowerName)) return true;
            if (lower.equals(lowerName + "123")) return true;
            if (lower.equals(lowerName + "1")) return true;
        }

        if (WEAK_PASSWORDS.contains(lower)) return true;
        if (lower.chars().distinct().count() <= 1) return true;
        if (lower.length() >= 4 && lower.chars().distinct().count() <= 2) return true;
        if (isSequentialDigits(lower)) return true;

        return false;
    }

    static boolean isSequentialDigits(String s) {
        if (s == null || s.length() < 3) return false;
        if (!s.chars().allMatch(Character::isDigit)) return false;
        int dir = 0;
        for (int i = 1; i < s.length(); i++) {
            int diff = s.charAt(i) - s.charAt(i - 1);
            if (diff != 1 && diff != -1) return false;
            if (dir == 0) dir = diff;
            else if (dir != diff) return false;
        }
        return true;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public boolean changePassword(String username, String newPassword) throws DataAccessException {
        return db.changePassword(username, PasswordUtil.hash(newPassword));
    }

    public boolean deleteUser(String username) throws DataAccessException {
        return db.deleteUser(username);
    }

    public String getLastLoginInfo(String username) throws DataAccessException {
        return db.getLastLoginInfo(username);
    }

    public int getMinPasswordLength() {
        return settings().auth().minPasswordLength();
    }

    public int getMaxPasswordLength() {
        return settings().auth().maxPasswordLength();
    }

    public AuthDatabase getDb() {
        return db;
    }

    private String getIpOrNull(Player player) {
        if (player.getAddress() == null) return null;
        return player.getAddress().getAddress().getHostAddress();
    }

    private void sendSyncMessage(Player player, byte type) {
        Settings.Velocity v = settings().velocity();
        if (!v.enabled() || !v.hasSecret()) return;
        AuthMessage msg = new AuthMessage(type, player.getUniqueId().toString(), player.getName());
        try {
            player.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes(v.secret()));
        } catch (Exception e) {
            plugin.getLogger().warning("[Messaging] sendSyncMessage hatası: " + e.getMessage());
        }
    }

    public void sendForceLogout(Player target) {
        Settings.Velocity v = settings().velocity();
        if (!v.enabled() || !v.hasSecret()) return;
        AuthMessage msg = new AuthMessage(MessageConstants.FORCE_LOGOUT, target.getUniqueId().toString(), target.getName());
        runOnMainThread(() -> {
            try {
                target.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes(v.secret()));
            } catch (Exception e) {
                plugin.getLogger().warning("[Messaging] sendForceLogout hatası: " + e.getMessage());
            }
        });
    }

    public enum RegisterResult {
        SUCCESS, ALREADY_REGISTERED, PASSWORD_MISMATCH,
        PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_INVALID,
        FAILED, IP_LIMIT, PENDING_EMAIL_VERIFICATION,
        EMAIL_INVALID, EMAIL_ALREADY_USED, DB_ERROR
    }
}
