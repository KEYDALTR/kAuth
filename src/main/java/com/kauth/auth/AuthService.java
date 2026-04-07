package com.kauth.auth;

import com.kauth.storage.DatabaseManager;
import com.kauth.storage.PasswordUtil;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AuthService {

    private final Plugin plugin;
    private final DatabaseManager db;
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sessionCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> sessionIpCache = new ConcurrentHashMap<>();
    // IP bazlı brute-force koruması
    private final Map<String, Integer> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBlockedUntil = new ConcurrentHashMap<>();
    // IP bazlı hesap limiti
    private final Map<String, Integer> ipRegisterCount = new ConcurrentHashMap<>();

    public AuthService(Plugin plugin, DatabaseManager db) {
        this.plugin = plugin;
        this.db = db;

        // Her 5 dakikada IP attempt sayaçlarını sıfırla
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ipAttempts.clear();
            ipRegisterCount.clear();
            // Süresi dolmuş blokları temizle
            long now = System.currentTimeMillis();
            ipBlockedUntil.entrySet().removeIf(e -> e.getValue() < now);
        }, 6000L, 6000L);
    }

    public boolean isRegistered(String playerName) {
        return db.isRegistered(playerName);
    }

    public boolean isAuthenticated(Player player) {
        return authenticatedPlayers.contains(player.getUniqueId());
    }

    public void setAuthenticated(Player player, boolean authenticated) {
        if (authenticated) {
            authenticatedPlayers.add(player.getUniqueId());
            loginAttempts.remove(player.getUniqueId());
            String ip = getIp(player);
            ipAttempts.remove(ip);
            db.updateLastLogin(player.getName(), ip);
            int sessionTimeout = plugin.getConfig().getInt("auth.session-timeout", 0);
            if (sessionTimeout > 0) {
                sessionCache.put(player.getUniqueId(), System.currentTimeMillis());
                sessionIpCache.put(player.getUniqueId(), ip);
            }
        } else {
            authenticatedPlayers.remove(player.getUniqueId());
            db.setLoggedIn(player.getName(), false);
        }
    }

    public boolean hasValidSession(Player player) {
        int sessionTimeout = plugin.getConfig().getInt("auth.session-timeout", 0);
        if (sessionTimeout <= 0) return false;
        UUID uuid = player.getUniqueId();
        if (!sessionCache.containsKey(uuid)) return false;
        long lastLogin = sessionCache.get(uuid);
        String lastIp = sessionIpCache.get(uuid);
        String currentIp = getIp(player);
        if (!currentIp.equals(lastIp)) return false;
        long elapsed = System.currentTimeMillis() - lastLogin;
        return elapsed < (sessionTimeout * 60_000L);
    }

    // IP bazlı brute-force kontrolü
    public boolean isIpBlocked(Player player) {
        String ip = getIp(player);
        Long blockedUntil = ipBlockedUntil.get(ip);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) return true;
        int maxIpAttempts = plugin.getConfig().getInt("auth.max-ip-attempts", 20);
        if (maxIpAttempts <= 0) return false;
        return ipAttempts.getOrDefault(ip, 0) >= maxIpAttempts;
    }

    public void incrementIpAttempts(Player player) {
        String ip = getIp(player);
        int current = ipAttempts.merge(ip, 1, Integer::sum);
        int maxIpAttempts = plugin.getConfig().getInt("auth.max-ip-attempts", 20);
        if (maxIpAttempts > 0 && current >= maxIpAttempts) {
            int blockMinutes = plugin.getConfig().getInt("auth.ip-block-minutes", 10);
            ipBlockedUntil.put(ip, System.currentTimeMillis() + blockMinutes * 60_000L);
            plugin.getLogger().warning("[Güvenlik] IP engellendi: " + ip + " (" + blockMinutes + " dakika)");
        }
    }

    // IP bazlı hesap limiti
    public boolean canRegisterFromIp(Player player) {
        int maxPerIp = plugin.getConfig().getInt("auth.max-accounts-per-ip", 3);
        if (maxPerIp <= 0) return true;
        String ip = getIp(player);
        int count = db.getAccountCountByIp(ip);
        return count < maxPerIp;
    }

    public void removePlayer(UUID uuid) {
        authenticatedPlayers.remove(uuid);
        loginAttempts.remove(uuid);
    }

    public boolean checkPassword(String playerName, String password) {
        String storedHash = db.getHashedPassword(playerName);
        return PasswordUtil.verify(password, storedHash);
    }

    public void forceLogin(Player player) {
        setAuthenticated(player, true);
    }

    public int incrementAttempts(Player player) {
        int current = loginAttempts.merge(player.getUniqueId(), 1, Integer::sum);
        incrementIpAttempts(player);
        return current;
    }

    public int getMaxAttempts() {
        return plugin.getConfig().getInt("auth.max-login-attempts", 5);
    }

    public int getRemainingAttempts(Player player) {
        int max = getMaxAttempts();
        if (max <= 0) return -1;
        int used = loginAttempts.getOrDefault(player.getUniqueId(), 0);
        return max - used;
    }

    public RegisterResult register(Player player, String password, String confirmPassword) {
        if (isRegistered(player.getName())) return RegisterResult.ALREADY_REGISTERED;
        if (!password.equals(confirmPassword)) return RegisterResult.PASSWORD_MISMATCH;

        int minLen = plugin.getConfig().getInt("auth.min-password-length", 4);
        int maxLen = plugin.getConfig().getInt("auth.max-password-length", 30);
        if (password.length() < minLen) return RegisterResult.PASSWORD_TOO_SHORT;
        if (password.length() > maxLen) return RegisterResult.PASSWORD_TOO_LONG;

        // Zayıf şifre kontrolü
        if (isWeakPassword(password, player.getName())) return RegisterResult.PASSWORD_INVALID;

        // IP bazlı hesap limiti
        if (!canRegisterFromIp(player)) return RegisterResult.IP_LIMIT;

        String hashedPassword = PasswordUtil.hash(password);
        String ip = getIp(player);

        boolean success = db.register(player.getName(), player.getUniqueId(), hashedPassword, ip);
        if (success) {
            setAuthenticated(player, true);
            return RegisterResult.SUCCESS;
        }
        return RegisterResult.FAILED;
    }

    private boolean isWeakPassword(String password, String playerName) {
        if (!plugin.getConfig().getBoolean("auth.block-weak-passwords", true)) return false;
        String lower = password.toLowerCase();
        // Oyuncu adıyla aynı
        if (lower.equals(playerName.toLowerCase())) return true;
        // Yaygın zayıf şifreler
        String[] weak = {"1234", "12345", "123456", "password", "sifre", "qwerty", "abcdef", "111111", "000000"};
        for (String w : weak) if (lower.equals(w)) return true;
        // Tek karakter tekrarı
        if (lower.chars().distinct().count() <= 1) return true;
        return false;
    }

    // Şifre değiştirme - eski şifrenin hash'ini güncelle (migration)
    public boolean changePassword(String username, String newPassword) {
        return db.changePassword(username, PasswordUtil.hash(newPassword));
    }

    public boolean deleteUser(String username) {
        return db.deleteUser(username);
    }

    // Son giriş bilgisi
    public String getLastLoginInfo(String username) {
        return db.getLastLoginInfo(username);
    }

    public int getMinPasswordLength() {
        return plugin.getConfig().getInt("auth.min-password-length", 4);
    }

    public int getMaxPasswordLength() {
        return plugin.getConfig().getInt("auth.max-password-length", 30);
    }

    private String getIp(Player player) {
        return player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
    }

    public enum RegisterResult {
        SUCCESS, ALREADY_REGISTERED, PASSWORD_MISMATCH,
        PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_INVALID,
        FAILED, IP_LIMIT
    }
}
