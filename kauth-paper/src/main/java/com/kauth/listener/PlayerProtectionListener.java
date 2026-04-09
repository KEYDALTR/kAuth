package com.kauth.listener;

import com.kauth.auth.AuthService;
import com.kauth.config.Settings;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;

/**
 * Giriş yapmamış oyuncuyu korumak için event listener.
 *
 * OPTIMIZE NOTLARI:
 * - Hot-path config okumaları Settings cache'inden yapılır
 * - Authenticated check en başta (en yaygın senaryo: zaten giriş yapmış)
 * - ignoreCancelled=true → başka plugin cancel'lamışsa skip
 * - PlayerMoveEvent'te hasChangedBlock() vanilla Paper shortcut'u
 */
public class PlayerProtectionListener implements Listener {

    private final AuthService auth;
    private final Settings settings;

    public PlayerProtectionListener(Plugin plugin, AuthService auth, Settings settings) {
        this.auth = auth;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!settings.blockChat) return;
        if (!auth.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!settings.blockCommands) return;
        if (auth.isAuthenticated(event.getPlayer())) return;
        if (!settings.isCommandAllowed(event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!settings.freezePlayer) return;
        // Block-level change kontrolü - yaw/pitch için tetiklenmesin
        if (!event.hasChangedBlock()) return;
        if (!auth.isAuthenticated(event.getPlayer())) {
            event.setTo(event.getFrom());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!auth.isAuthenticated(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!auth.isAuthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!auth.isAuthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && !auth.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && !auth.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (!auth.isAuthenticated(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && !auth.isAuthenticated(player)) {
            event.setCancelled(true);
        }
    }
}
