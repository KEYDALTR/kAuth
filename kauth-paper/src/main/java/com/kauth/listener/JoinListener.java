package com.kauth.listener;

import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
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

public class JoinListener implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Plugin plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final DialogProvider dialogProvider;

    // Her oyuncu için tek bir session state (tüm task'lar + bossbar burada)
    private final Map<UUID, AuthSession> sessions = new HashMap<>();

    public JoinListener(Plugin plugin, AuthService auth, ConfigManager config, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.dialogProvider = dialogProvider;
    }

    /**
     * Oyuncu bazlı auth ekranı state'i. Tüm task ve kaynaklar burada.
     * Config değerleri join anında 1 kez okunur, her tick lookup yapılmaz.
     */
    private static class AuthSession {
        BukkitTask updateTask;      // actionbar + bossbar + title countdown (unified)
        BukkitTask soundTask;       // ses döngüsü
        BukkitTask timeoutTask;     // kick timer
        BossBar bossBar;
        long startTime;
        int timeoutSeconds;
        // Cached config values
        boolean abEnabled;
        String abMsg;
        int abTicks;
        boolean bbEnabled;
        String bbTitle;
        boolean tcEnabled;
        String tcTitle;
        String tcSubtitle;
    }

    // ====================================================================
    // LISTENERS
    // ====================================================================

    /**
     * Prelogin - IP blokluysa sunucuya hiç sokma (resource tasarrufu).
     */
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

        // Oturum kontrolü (aynı IP + süre içinde tekrar giriş)
        if (auth.isRegistered(player.getName()) && auth.hasValidSession(player)) {
            auth.forceLogin(player);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage(config.msgComponent("session.resumed"));
                }
            }, 5L);
            return;
        }

        auth.setAuthenticated(player, false);
        beginAuthFlow(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        auth.removePlayer(uuid);
        teardownSession(event.getPlayer(), uuid);
    }

    // ====================================================================
    // AUTH FLOW - Join / Logout ortak giriş ekranı kurulumu
    // ====================================================================

    /**
     * Auth ekranını başlat: körlük, efekt, dialog, unified update task,
     * ses döngüsü, timeout kick. Hem join hem /cikis sonrası çağrılır.
     */
    public void beginAuthFlow(Player player) {
        UUID uuid = player.getUniqueId();
        // Eski session varsa temizle (çift task olmasın)
        teardownSession(player, uuid);

        AuthSession session = new AuthSession();
        session.startTime = System.currentTimeMillis();
        session.timeoutSeconds = plugin.getConfig().getInt("auth.login-timeout", 60);

        // Config değerlerini 1 kez oku (her tick lookup engelle)
        session.abEnabled = plugin.getConfig().getBoolean("effects.waiting.actionbar.enabled", true);
        session.abMsg = plugin.getConfig().getString("effects.waiting.actionbar.message", "");
        session.abTicks = plugin.getConfig().getInt("effects.waiting.actionbar.update-ticks", 20);
        session.bbEnabled = plugin.getConfig().getBoolean("effects.waiting.bossbar.enabled", false)
                && session.timeoutSeconds > 0;
        session.bbTitle = plugin.getConfig().getString("effects.waiting.bossbar.title",
                "<color:#FF6B6B>⏱ %time% sn</color>");
        session.tcEnabled = plugin.getConfig().getBoolean("effects.waiting.title-countdown.enabled", true)
                && session.timeoutSeconds > 0;
        session.tcTitle = plugin.getConfig().getString("effects.waiting.title-countdown.title",
                "<color:#FF6B6B>⏱ %time% sn</color>");
        session.tcSubtitle = plugin.getConfig().getString("effects.waiting.title-countdown.subtitle",
                "<color:#B0C4D4>Lütfen giriş yapınız</color>");

        sessions.put(uuid, session);

        // Körlük efekti
        if (plugin.getConfig().getBoolean("auth.blind-effect", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }

        // Bekleme başlangıç title/efekt (1 kez)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || auth.isAuthenticated(player)) return;
            EffectUtil.playEffect(plugin, player, "waiting");
        }, 5L);

        // Dialog göster
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || auth.isAuthenticated(player)) return;
            if (auth.isRegistered(player.getName())) {
                dialogProvider.showLogin(player);
            } else {
                if (config.ruleEnabled()) {
                    dialogProvider.showRules(player);
                } else {
                    dialogProvider.showRegister(player);
                }
            }
        }, 10L);

        // Bossbar oluştur
        if (session.bbEnabled) {
            BossBar.Color bbColor = parseColor(plugin.getConfig().getString("effects.waiting.bossbar.color", "RED"));
            BossBar.Overlay bbOverlay = parseOverlay(plugin.getConfig().getString("effects.waiting.bossbar.overlay", "PROGRESS"));
            session.bossBar = BossBar.bossBar(
                    MM.deserialize(session.bbTitle.replace("%time%", String.valueOf(session.timeoutSeconds))),
                    1.0f, bbColor, bbOverlay);
            player.showBossBar(session.bossBar);
        }

        // UNIFIED UPDATE TASK - actionbar + bossbar + title countdown (tek timer, 1 sn aralık)
        if (session.abEnabled || session.bbEnabled || session.tcEnabled) {
            session.updateTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || auth.isAuthenticated(player)) return;
                long elapsed = (System.currentTimeMillis() - session.startTime) / 1000;
                long remaining = session.timeoutSeconds > 0
                        ? Math.max(0, session.timeoutSeconds - elapsed) : 0;
                String timeStr = String.valueOf(remaining);

                if (session.abEnabled && !session.abMsg.isEmpty()) {
                    player.sendActionBar(MM.deserialize(session.abMsg.replace("%time%", timeStr)));
                }
                if (session.bbEnabled && session.bossBar != null) {
                    float progress = session.timeoutSeconds > 0
                            ? Math.max(0f, Math.min(1f, (float) remaining / (float) session.timeoutSeconds))
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
            }, 20L, 20L);
        }

        // Arkaplan ses döngüsü
        if (plugin.getConfig().getBoolean("effects.waiting.sound-loop.enabled", false)) {
            final String soundType = plugin.getConfig().getString("effects.waiting.sound-loop.type", "BLOCK_NOTE_BLOCK_HAT");
            final float volume = (float) plugin.getConfig().getDouble("effects.waiting.sound-loop.volume", 0.5);
            final float pitch = (float) plugin.getConfig().getDouble("effects.waiting.sound-loop.pitch", 1.0);
            final int intervalTicks = plugin.getConfig().getInt("effects.waiting.sound-loop.interval-ticks", 40);
            session.soundTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                if (!player.isOnline() || auth.isAuthenticated(player)) return;
                try {
                    player.playSound(player.getLocation(), Sound.valueOf(soundType), volume, pitch);
                } catch (IllegalArgumentException ignored) {}
            }, 20L, intervalTicks);
        }

        // Timeout kick
        if (session.timeoutSeconds > 0) {
            session.timeoutTask = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !auth.isAuthenticated(player)) {
                    String msg = plugin.getConfig().getString("auth.timeout-kick-message",
                            "<red>Giriş süresi doldu!</red>");
                    player.kick(config.parse(msg));
                }
            }, session.timeoutSeconds * 20L);
        }
    }

    /**
     * Session'ı tamamen kapat: task'ları iptal, bossbar kaldır.
     * Player null olabilir (onQuit'te player geçilmeli).
     */
    private void teardownSession(Player player, UUID uuid) {
        AuthSession session = sessions.remove(uuid);
        if (session == null) return;
        if (session.updateTask != null) session.updateTask.cancel();
        if (session.soundTask != null) session.soundTask.cancel();
        if (session.timeoutTask != null) session.timeoutTask.cancel();
        if (session.bossBar != null && player != null) {
            try { player.hideBossBar(session.bossBar); } catch (Exception ignored) {}
        }
    }

    /**
     * Auth başarılı → task'ları iptal, ekran temizlensin.
     * AuthService.registerAuthSuccessHandler ile bağlanır.
     */
    public void onAuthSuccess(Player player) {
        UUID uuid = player.getUniqueId();
        teardownSession(player, uuid);
        try { player.clearTitle(); } catch (Exception ignored) {}
        try { player.sendActionBar(Component.empty()); } catch (Exception ignored) {}
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
