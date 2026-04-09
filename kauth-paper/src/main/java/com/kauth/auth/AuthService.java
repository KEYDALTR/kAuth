package com.kauth.auth;

import com.kauth.KAuth;
import com.kauth.common.messaging.AuthMessage;
import com.kauth.common.messaging.MessageConstants;
import com.kauth.common.storage.AuthDatabase;
import com.kauth.common.storage.PasswordUtil;
import com.kauth.config.Settings;
import com.kauth.messaging.MessagingHelper;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AuthService {

    /** Tek allocation ile oturum bilgisini tutar (timestamp + IP). */
    private record SessionEntry(long loginTime, String ip) {}

    private final Plugin plugin;
    private final AuthDatabase db;
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, SessionEntry> sessions = new ConcurrentHashMap<>();
    private final Map<String, Integer> ipAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> ipBlockedUntil = new ConcurrentHashMap<>();
    private final Map<String, Integer> ipRegisterCount = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> authSuccessHandlers = new ArrayList<>();

    public AuthService(Plugin plugin, AuthDatabase db) {
        this.plugin = plugin;
        this.db = db;

        // Her 5 dakikada IP attempt sayaçlarını sıfırla
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            ipAttempts.clear();
            ipRegisterCount.clear();
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
        UUID uuid = player.getUniqueId();
        if (authenticated) {
            authenticatedPlayers.add(uuid);
            loginAttempts.remove(uuid);
            String ip = getIp(player);
            ipAttempts.remove(ip);
            db.updateLastLogin(player.getName(), ip);
            int sessionTimeout = settings().sessionTimeoutMinutes;
            if (sessionTimeout > 0) {
                sessions.put(uuid, new SessionEntry(System.currentTimeMillis(), ip));
            }
            sendSyncMessage(player, MessageConstants.LOGIN);
            fireAuthSuccess(player);
        } else {
            authenticatedPlayers.remove(uuid);
            // Manuel logout → session cache temizle ki reconnect'te auto-login olmasın
            sessions.remove(uuid);
            db.setLoggedIn(player.getName(), false);
            sendSyncMessage(player, MessageConstants.LOGOUT);
        }
    }

    private Settings settings() {
        return ((KAuth) plugin).getConfigManager().getSettings();
    }

    /**
     * Auth başarılı olduğunda çağrılacak handler kaydet.
     * JoinListener countdown task'ını iptal etmek için kullanır.
     */
    public void registerAuthSuccessHandler(Consumer<Player> handler) {
        authSuccessHandlers.add(handler);
    }

    private void fireAuthSuccess(Player player) {
        // Her zaman main thread'de çalışsın
        Runnable r = () -> {
            for (Consumer<Player> h : authSuccessHandlers) {
                try { h.accept(player); } catch (Exception ignored) {}
            }
        };
        if (plugin.getServer().isPrimaryThread()) {
            r.run();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, r);
        }
    }

    /**
     * Velocity'den gelen remote login - şifre kontrolü yapmadan authenticate et.
     */
    public void forceLoginRemote(Player player) {
        authenticatedPlayers.add(player.getUniqueId());
        loginAttempts.remove(player.getUniqueId());
        fireAuthSuccess(player);
    }

    public boolean hasValidSession(Player player) {
        int sessionTimeout = settings().sessionTimeoutMinutes;
        if (sessionTimeout <= 0) return false;
        SessionEntry entry = sessions.get(player.getUniqueId());
        if (entry == null) return false;
        if (!getIp(player).equals(entry.ip())) return false;
        long elapsed = System.currentTimeMillis() - entry.loginTime();
        return elapsed < (sessionTimeout * 60_000L);
    }

    public boolean isIpBlocked(Player player) {
        return isIpBlocked(getIp(player));
    }

    public boolean isIpBlocked(String ip) {
        Long blockedUntil = ipBlockedUntil.get(ip);
        if (blockedUntil != null && System.currentTimeMillis() < blockedUntil) return true;
        int maxIpAttempts = settings().maxIpAttempts;
        if (maxIpAttempts <= 0) return false;
        return ipAttempts.getOrDefault(ip, 0) >= maxIpAttempts;
    }

    /**
     * IP ban'ın biteceği zaman (ms). Bloklu değilse 0 döner.
     */
    public long getIpBlockedUntil(String ip) {
        Long until = ipBlockedUntil.get(ip);
        return until == null ? 0 : until;
    }

    /**
     * IP ban'ın kalan süresi (saniye). Bloklu değilse 0 döner.
     */
    public long getIpBlockRemainingSeconds(String ip) {
        long until = getIpBlockedUntil(ip);
        if (until == 0) return 0;
        long remaining = (until - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    public void incrementIpAttempts(Player player) {
        String ip = getIp(player);
        int current = ipAttempts.merge(ip, 1, Integer::sum);
        Settings s = settings();
        int maxIpAttempts = s.maxIpAttempts;
        if (maxIpAttempts > 0 && current >= maxIpAttempts) {
            int blockMinutes = s.ipBlockMinutes;
            ipBlockedUntil.put(ip, System.currentTimeMillis() + blockMinutes * 60_000L);
            plugin.getLogger().warning("[Güvenlik] IP engellendi: " + ip + " (" + blockMinutes + " dakika)");
        }
    }

    public boolean canRegisterFromIp(Player player) {
        int maxPerIp = settings().maxAccountsPerIp;
        if (maxPerIp <= 0) return true;
        String ip = getIp(player);
        int count = db.getAccountCountByIp(ip);
        return count < maxPerIp;
    }

    public void removePlayer(UUID uuid) {
        authenticatedPlayers.remove(uuid);
        loginAttempts.remove(uuid);
        // NOT: sessions'ı silmiyoruz → quit sonrası valid session varsa reconnect'te auto-login
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
        return settings().maxLoginAttempts;
    }

    public int getRemainingAttempts(Player player) {
        int max = getMaxAttempts();
        if (max <= 0) return -1;
        int used = loginAttempts.getOrDefault(player.getUniqueId(), 0);
        return max - used;
    }

    public RegisterResult register(Player player, String password, String confirmPassword) {
        return register(player, password, confirmPassword, null);
    }

    public RegisterResult register(Player player, String password, String confirmPassword, String email) {
        if (isRegistered(player.getName())) return RegisterResult.ALREADY_REGISTERED;
        Settings s = settings();
        if (s.requirePasswordConfirmation && !password.equals(confirmPassword)) return RegisterResult.PASSWORD_MISMATCH;

        if (password.length() < s.minPasswordLength) return RegisterResult.PASSWORD_TOO_SHORT;
        if (password.length() > s.maxPasswordLength) return RegisterResult.PASSWORD_TOO_LONG;

        if (isWeakPassword(password, player.getName())) return RegisterResult.PASSWORD_INVALID;
        if (!canRegisterFromIp(player)) return RegisterResult.IP_LIMIT;

        // E-posta kontrolü
        if (email != null && !email.isEmpty()) {
            if (!isValidEmail(email)) return RegisterResult.EMAIL_INVALID;
            if (db.isEmailUsed(email)) return RegisterResult.EMAIL_ALREADY_USED;
        }

        String hashedPassword = PasswordUtil.hash(password);
        String ip = getIp(player);

        boolean success;
        if (email != null && !email.isEmpty()) {
            success = db.registerWithEmail(player.getName(), player.getUniqueId().toString(), hashedPassword, ip, email);
        } else {
            success = db.register(player.getName(), player.getUniqueId().toString(), hashedPassword, ip);
        }

        if (success) {
            // E-posta doğrulaması gerekiyorsa hemen authenticate etme
            boolean emailVerificationRequired = s.emailEnabled
                    && s.emailVerificationRequired
                    && email != null && !email.isEmpty();

            if (emailVerificationRequired) {
                return RegisterResult.PENDING_EMAIL_VERIFICATION;
            }

            setAuthenticated(player, true);
            return RegisterResult.SUCCESS;
        }
        return RegisterResult.FAILED;
    }

    private boolean isWeakPassword(String password, String playerName) {
        if (!settings().blockWeakPasswords) return false;
        String lower = password.toLowerCase();
        if (lower.equals(playerName.toLowerCase())) return true;
        String[] weak = {"1234", "12345", "123456", "password", "sifre", "qwerty", "abcdef", "111111", "000000"};
        for (String w : weak) if (lower.equals(w)) return true;
        if (lower.chars().distinct().count() <= 1) return true;
        return false;
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }

    public boolean changePassword(String username, String newPassword) {
        return db.changePassword(username, PasswordUtil.hash(newPassword));
    }

    public boolean deleteUser(String username) {
        return db.deleteUser(username);
    }

    public String getLastLoginInfo(String username) {
        return db.getLastLoginInfo(username);
    }

    public int getMinPasswordLength() {
        return settings().minPasswordLength;
    }

    public int getMaxPasswordLength() {
        return settings().maxPasswordLength;
    }

    public AuthDatabase getDb() {
        return db;
    }

    private String getIp(Player player) {
        return player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "unknown";
    }

    private void sendSyncMessage(Player player, byte type) {
        if (!settings().velocityEnabled) return;
        AuthMessage msg = new AuthMessage(type, player.getUniqueId().toString(), player.getName());
        player.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes());
    }

    /**
     * Admin işlemlerinde (kayıt silme, şifre değiştirme) force logout gönder.
     */
    public void sendForceLogout(Player target) {
        if (!settings().velocityEnabled) return;
        AuthMessage msg = new AuthMessage(MessageConstants.FORCE_LOGOUT, target.getUniqueId().toString(), target.getName());
        target.sendPluginMessage(plugin, MessageConstants.CHANNEL, msg.toBytes());
    }

    public enum RegisterResult {
        SUCCESS, ALREADY_REGISTERED, PASSWORD_MISMATCH,
        PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORD_INVALID,
        FAILED, IP_LIMIT, PENDING_EMAIL_VERIFICATION,
        EMAIL_INVALID, EMAIL_ALREADY_USED
    }
}
