package com.kauth.listener;

import com.kauth.auth.AuthService;
import com.kauth.config.ConfigManager;
import com.kauth.dialog.DialogProvider;
import com.kauth.util.EffectUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JoinListener implements Listener {

    private final Plugin plugin;
    private final AuthService auth;
    private final ConfigManager config;
    private final DialogProvider dialogProvider;
    private final Map<UUID, BukkitTask> timeoutTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> actionbarTasks = new HashMap<>();

    public JoinListener(Plugin plugin, AuthService auth, ConfigManager config, DialogProvider dialogProvider) {
        this.plugin = plugin;
        this.auth = auth;
        this.config = config;
        this.dialogProvider = dialogProvider;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Oturum kontrolü
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

        // Körlük efekti
        if (plugin.getConfig().getBoolean("auth.blind-effect", true)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, Integer.MAX_VALUE, 0, false, false, false));
        }

        // Bekleme title efekti
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || auth.isAuthenticated(player)) return;
            EffectUtil.playEffect(plugin, player, "waiting");
        }, 5L);

        // Dialog veya chat mesajı göster
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

        // Bekleme actionbar
        if (plugin.getConfig().getBoolean("effects.waiting.actionbar.enabled", true)) {
            int abTicks = plugin.getConfig().getInt("effects.waiting.actionbar.update-ticks", 20);
            String abMsg = plugin.getConfig().getString("effects.waiting.actionbar.message", "");
            if (!abMsg.isEmpty()) {
                final String msg = abMsg;
                BukkitTask abTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
                    if (!player.isOnline() || auth.isAuthenticated(player)) return;
                    player.sendActionBar(MiniMessage.miniMessage().deserialize(msg));
                }, 15L, abTicks);
                actionbarTasks.put(player.getUniqueId(), abTask);
            }
        }

        // Timeout
        int timeout = plugin.getConfig().getInt("auth.login-timeout", 60);
        if (timeout > 0) {
            BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !auth.isAuthenticated(player)) {
                    String msg = plugin.getConfig().getString("auth.timeout-kick-message",
                            "<red>Giriş süresi doldu!</red>");
                    player.kick(config.parse(msg));
                }
                timeoutTasks.remove(player.getUniqueId());
            }, timeout * 20L);
            timeoutTasks.put(player.getUniqueId(), task);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        auth.removePlayer(uuid);
        BukkitTask task = timeoutTasks.remove(uuid);
        if (task != null) task.cancel();
        BukkitTask abTask = actionbarTasks.remove(uuid);
        if (abTask != null) abTask.cancel();
    }
}
