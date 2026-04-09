package com.kauth.listener;

import com.kauth.KAuth;
import com.kauth.auth.AuthService;
import com.kauth.common.storage.DataAccessException;
import com.kauth.config.ConfigManager;
import com.kauth.config.Settings;
import com.kauth.dialog.DialogProvider;
import com.kauth.util.EffectUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class JoinListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KAuth plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final DialogProvider dialogProvider;

    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();

    public JoinListener(KAuth plugin, AuthService auth, ConfigManager config, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.dialogProvider = dialogProvider;
    }

    private static class AuthSession {
        BukkitTask updateTask;
        BukkitTask soundTask;
        BukkitTask timeoutTask;
        BossBar bossBar;
        final long startTime;
        final int timeoutSeconds;
        final boolean abEnabled;
        final String abMsg;
        final int abTicks;
        final boolean bbEnabled;
        final String bbTitle;
        final boolean tcEnabled;
        final String tcTitle;
        final String tcSubtitle;

        AuthSession(long startTime, int timeoutSeconds, boolean abEnabled, String abMsg, int abTicks,
                    boolean bbEnabled, String bbTitle, boolean tcEnabled, String tcTitle, String tcSubtitle) {
            this.startTime = startTime;
            this.timeoutSeconds = timeoutSeconds;
            this.abEnabled = abEnabled;
            this.abMsg = abMsg;
            this.abTicks = abTicks;
            this.bbEnabled = bbEnabled;
            this.bbTitle = bbTitle;
            this.tcEnabled = tcEnabled;
            this.tcTitle = tcTitle;
            this.tcSubtitle = tcSubtitle;
        }

        void cancelTasks() {
            if (updateTask != null) { try { updateTask.cancel(); } catch (Exception ignored) {} }
            if (soundTask != null) { try { soundTask.cancel(); } catch (Exception ignored) {} }
            if (timeoutTask != null) { try { timeoutTask.cancel(); } catch (Exception ignored) {} }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!plugin.getConfig().getBoolean("auth.ip-ban-on-prelogin", true)) return;
        String ip = event.getAddress().getHostAddress();
        if (auth.isIpBlocked(ip)) {
            long remaining = auth.getIpBlockRemainingSeconds(ip);
            String msgTemplate = plugin.getConfig().getString("auth.ip-block-kick-message",
                    "<color:#FF6B6B>IP adresiniz geçici olarak engellendi!</color><newline><color:#B0C4D4>Kalan süre: <color:#FFD93D>%time%</color> saniye</color>");
            String msg = msgTemplate.replace("%time%", String.valueOf(remaining));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, MM.deserialize(msg));
            plugin.getLogger().warning("[Güvenlik] Bloklu IP bağlanmaya çalıştı: " + ip
                    + " (kalan: " + remaining + "s)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        try {
            if (auth.isRegistered(player.getName()) && auth.hasValidSession(player)) {
                auth.forceLogin(player);
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendMessage(config.msgComponent("session.resumed"));
                    }
                }, 5L);
                return;
            }
        } catch (DataAccessException e) {
            plugin.getLogger().log(Level.SEVERE, "[kAuth] onJoin isRegistered DB hatası", e);
            player.kick(config.parse("<color:#FF6B6B>Veritabanı hatası, tekrar deneyiniz.</color>"));
            return;
        }

        try {
            auth.setAuthenticated(player, false);
        } catch (DataAccessException e) {
            plugin.getLogger().log(Level.WARNING, "[kAuth] onJoin setAuthenticated(false) hatası", e);
        }
        beginAuthFlow(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        auth.removePlayer(uuid);
        teardownSession(event.getPlayer(), uuid);
    }

    public void beginAuthFlow(Player player) {
        UUID uuid = player.getUniqueId();
        teardownSession(player, uuid);

        Settings.Snapshot snap = plugin.getConfigManager().getSettings().get();
        Settings.Auth authCfg = snap.auth();

        int timeoutSeconds = authCfg.loginTimeout();

        boolean abEnabled = plugin.getConfig().getBoolean("effects.waiting.actionbar.enabled", true);
        String abMsg = plugin.getConfig().getString("effects.waiting.actionbar.message", "");
        int abTicks = plugin.getConfig().getInt("effects.waiting.actionbar.update-ticks", 20);

        boolean bbEnabled = plugin.getConfig().getBoolean("effects.waiting.bossbar.enabled", false)
                && timeoutSeconds > 0;
        String bbTitle = plugin.getConfig().getString("effects.waiting.bossbar.title",
                "<color:#FF6B6B>⏱ %time% sn</color>");

        boolean tcEnabled = plugin.getConfig().getBoolean("effects.waiting.title-countdown.enabled", true)
                && timeoutSeconds > 0;
        String tcTitle = plugin.getConfig().getString("effects.waiting.title-countdown.title",
                "<color:#FF6B6B>⏱ %time% sn</color>");
        String tcSubtitle = plugin.getConfig().getString("effects.waiting.title-countdown.subtitle",
                "<color:#B0C4D4>Lütfen giriş yapınız</color>");

        AuthSession session = new AuthSession(
                System.currentTimeMillis(), timeoutSeconds,
                abEnabled, abMsg, abTicks,
                bbEnabled, bbTitle,
                tcEnabled, tcTitle, tcSubtitle
        );
        sessions.put(uuid, session);

        if (authCfg.blindEffect()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || auth.isAuthenticated(player)) return;
            EffectUtil.playEffect(plugin, player, "waiting");
        }, 5L);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || auth.isAuthenticated(player)) return;
            try {
                if (auth.isRegistered(player.getName())) {
                    dialogProvider.showLogin(player);
                } else {
                    if (config.ruleEnabled()) dialogProvider.showRules(player);
                    else dialogProvider.showRegister(player);
                }
            } catch (DataAccessException e) {
                plugin.getLogger().log(Level.SEVERE, "[kAuth] beginAuthFlow isRegistered hatası", e);
                player.kick(config.parse("<color:#FF6B6B>Veritabanı hatası.</color>"));
            }
        }, 10L);

        if (bbEnabled) {
            BossBar.Color bbColor = parseColor(plugin.getConfig().getString("effects.waiting.bossbar.color", "RED"));
            BossBar.Overlay bbOverlay = parseOverlay(plugin.getConfig().getString("effects.waiting.bossbar.overlay", "PROGRESS"));
            session.bossBar = BossBar.bossBar(
                    MM.deserialize(bbTitle.replace("%time%", String.valueOf(timeoutSeconds))),
                    1.0f, bbColor, bbOverlay);
            player.showBossBar(session.bossBar);
        }

        if (abEnabled || bbEnabled || tcEnabled) {
            session.updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || auth.isAuthenticated(player)) {
                    AuthSession s = sessions.get(uuid);
                    if (s != null && s.updateTask != null) {
                        try { s.updateTask.cancel(); } catch (Exception ignored) {}
                    }
                    return;
                }
                try {
                    long elapsed = (System.currentTimeMillis() - session.startTime) / 1000;
                    long remaining = timeoutSeconds > 0 ? Math.max(0, timeoutSeconds - elapsed) : 0;
                    String timeStr = String.valueOf(remaining);

                    if (session.abEnabled && !session.abMsg.isEmpty()) {
                        player.sendActionBar(MM.deserialize(session.abMsg.replace("%time%", timeStr)));
                    }
                    if (session.bbEnabled && session.bossBar != null) {
                        float progress = timeoutSeconds > 0
                                ? Math.max(0f, Math.min(1f, (float) remaining / (float) timeoutSeconds))
                                : 1f;
                        session.bossBar.progress(progress);
                        session.bossBar.name(MM.deserialize(session.bbTitle.replace("%time%", timeStr)));
                    }
                    if (session.tcEnabled) {
                        Component title = MM.deserialize(session.tcTitle.replace("%time%", timeStr));
                        Component subtitle = MM.deserialize(session.tcSubtitle.replace("%time%", timeStr));
                        player.showTitle(Title.title(title, subtitle,
                                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ZERO)));
                    }
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[kAuth] Update task hatası: " + e.getMessage(), e);
                }
            }, 20L, 20L);
        }

        if (plugin.getConfig().getBoolean("effects.waiting.sound-loop.enabled", false)) {
            final String soundType = plugin.getConfig().getString("effects.waiting.sound-loop.type", "BLOCK_NOTE_BLOCK_HAT");
            final float volume = (float) plugin.getConfig().getDouble("effects.waiting.sound-loop.volume", 0.5);
            final float pitch = (float) plugin.getConfig().getDouble("effects.waiting.sound-loop.pitch", 1.0);
            final int intervalTicks = plugin.getConfig().getInt("effects.waiting.sound-loop.interval-ticks", 40);
            session.soundTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || auth.isAuthenticated(player)) {
                    AuthSession s = sessions.get(uuid);
                    if (s != null && s.soundTask != null) {
                        try { s.soundTask.cancel(); } catch (Exception ignored) {}
                    }
                    return;
                }
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(soundType), volume, pitch);
                } catch (IllegalArgumentException ignored) {}
            }, 20L, intervalTicks);
        }

        if (timeoutSeconds > 0) {
            session.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !auth.isAuthenticated(player)) {
                    String msg = plugin.getConfig().getString("auth.timeout-kick-message",
                            "<red>Giriş süresi doldu!</red>");
                    player.kick(config.parse(msg));
                }
            }, timeoutSeconds * 20L);
        }
    }

    private void teardownSession(Player player, UUID uuid) {
        AuthSession session = sessions.remove(uuid);
        if (session == null) return;
        session.cancelTasks();
        if (session.bossBar != null && player != null) {
            try { player.hideBossBar(session.bossBar); } catch (Exception ignored) {}
        }
    }

    public void onAuthSuccess(Player player) {
        UUID uuid = player.getUniqueId();
        teardownSession(player, uuid);
        try { player.clearTitle(); } catch (Exception ignored) {}
        try { player.sendActionBar(Component.empty()); } catch (Exception ignored) {}
    }

    public void shutdownAllSessions() {
        for (Map.Entry<UUID, AuthSession> e : sessions.entrySet()) {
            Player p = plugin.getServer().getPlayer(e.getKey());
            if (p != null) {
                teardownSession(p, e.getKey());
            } else {
                e.getValue().cancelTasks();
            }
        }
        sessions.clear();
    }

    private BossBar.Color parseColor(String s) {
        try { return BossBar.Color.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return BossBar.Color.RED; }
    }

    private BossBar.Overlay parseOverlay(String s) {
        try { return BossBar.Overlay.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return BossBar.Overlay.PROGRESS; }
    }
}
