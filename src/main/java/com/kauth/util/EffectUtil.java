package com.kauth.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;

public final class EffectUtil {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    private EffectUtil() {}

    public static void playEffect(Plugin plugin, Player player, String configSection) {
        // Title
        if (plugin.getConfig().getBoolean("effects." + configSection + ".title.enabled", false)) {
            String titleText = plugin.getConfig().getString("effects." + configSection + ".title.title", "");
            String subtitleText = plugin.getConfig().getString("effects." + configSection + ".title.subtitle", "");
            int fadeIn = plugin.getConfig().getInt("effects." + configSection + ".title.fade-in", 10);
            int stay = plugin.getConfig().getInt("effects." + configSection + ".title.stay", 40);
            int fadeOut = plugin.getConfig().getInt("effects." + configSection + ".title.fade-out", 10);

            titleText = titleText.replace("%player_name%", player.getName());
            subtitleText = subtitleText.replace("%player_name%", player.getName());

            Component title = mm.deserialize(titleText);
            Component subtitle = mm.deserialize(subtitleText);

            player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(
                            Duration.ofMillis(fadeIn * 50L),
                            Duration.ofMillis(stay * 50L),
                            Duration.ofMillis(fadeOut * 50L)
                    )));
        }

        // Sound
        if (plugin.getConfig().getBoolean("effects." + configSection + ".sound.enabled", false)) {
            try {
                String soundType = plugin.getConfig().getString("effects." + configSection + ".sound.type", "");
                float volume = (float) plugin.getConfig().getDouble("effects." + configSection + ".sound.volume", 1.0);
                float pitch = (float) plugin.getConfig().getDouble("effects." + configSection + ".sound.pitch", 1.0);
                if (!soundType.isEmpty()) {
                    player.playSound(player.getLocation(), Sound.valueOf(soundType), volume, pitch);
                }
            } catch (Exception ignored) {}
        }

        // Actionbar (tek seferlik)
        if (plugin.getConfig().getBoolean("effects." + configSection + ".actionbar.enabled", false)) {
            String msg = plugin.getConfig().getString("effects." + configSection + ".actionbar.message", "");
            if (!msg.isEmpty()) {
                msg = msg.replace("%player_name%", player.getName());
                player.sendActionBar(mm.deserialize(msg));
            }
        }
    }
}
