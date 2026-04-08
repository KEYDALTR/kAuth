package com.kauth.velocity;

import com.google.inject.Inject;
import com.kauth.common.messaging.MessageConstants;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "kauth",
        name = "kAuth",
        version = "1.0.2",
        description = "kAuth - Velocity Senkronizasyon",
        authors = {"Egemen KEYDAL"}
)
public class KAuthVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> ignoredServers = new HashSet<>();

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(MessageConstants.CHANNEL);

    @Inject
    public KAuthVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();

        server.getChannelRegistrar().register(CHANNEL);
        server.getEventManager().register(this, new VelocityMessageListener(this));
        server.getEventManager().register(this, new VelocityConnectionListener(this));

        logger.info("============================kAuth============================");
        logger.info("kAuth Velocity Senkronizasyon v1.0.2");
        logger.info("Kanal: " + MessageConstants.CHANNEL);
        if (!ignoredServers.isEmpty()) {
            logger.info("Yoksayılan sunucular (limbo/filtre): " + String.join(", ", ignoredServers));
        }
        logger.info("kAuth Velocity aktif!");
        logger.info("============================kAuth============================");
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }

            Path configFile = dataDirectory.resolve("config.properties");
            if (!Files.exists(configFile)) {
                // Varsayılan config oluştur
                String defaultConfig = """
                        # kAuth Velocity Konfigürasyonu
                        #
                        # Yoksayılacak sunucular (limbo, filtre, auth sunucuları)
                        # LimboFilter veya benzeri plugin kullanıyorsanız
                        # limbo sunucularının adlarını virgülle ayırarak yazın.
                        # Bu sunuculara auth senkronizasyon mesajı gönderilmez.
                        #
                        # Örnek: limbo,filter,auth
                        ignored-servers=limbo,filter
                        """;
                Files.writeString(configFile, defaultConfig);
                logger.info("Varsayılan config oluşturuldu: " + configFile);
            }

            // Config oku
            Properties props = new Properties();
            try (InputStream is = Files.newInputStream(configFile)) {
                props.load(is);
            }

            String ignored = props.getProperty("ignored-servers", "limbo,filter");
            ignoredServers.clear();
            for (String s : ignored.split(",")) {
                String trimmed = s.trim().toLowerCase();
                if (!trimmed.isEmpty()) {
                    ignoredServers.add(trimmed);
                }
            }

        } catch (IOException e) {
            logger.warn("Config yüklenemedi, varsayılan değerler kullanılıyor: " + e.getMessage());
            ignoredServers.add("limbo");
            ignoredServers.add("filter");
        }
    }

    /**
     * Belirtilen sunucu adı limbo/filtre sunucusu mu?
     * LimboFilter uyumluluğu için bu sunuculara mesaj gönderilmez.
     */
    public boolean isIgnoredServer(String serverName) {
        return ignoredServers.contains(serverName.toLowerCase());
    }

    public boolean isAuthenticated(UUID uuid) {
        return authenticatedPlayers.contains(uuid);
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (authenticated) {
            authenticatedPlayers.add(uuid);
        } else {
            authenticatedPlayers.remove(uuid);
        }
    }

    public ProxyServer getServer() {
        return server;
    }

    public Logger getLogger() {
        return logger;
    }
}
