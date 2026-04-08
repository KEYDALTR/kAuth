package com.kauth.velocity;

import com.google.inject.Inject;
import com.kauth.common.messaging.MessageConstants;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Plugin(
        id = "kauth",
        name = "kAuth",
        version = "1.0.1",
        description = "kAuth - Velocity Senkronizasyon",
        authors = {"Egemen KEYDAL"}
)
public class KAuthVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from(MessageConstants.CHANNEL);

    @Inject
    public KAuthVelocity(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        server.getEventManager().register(this, new VelocityMessageListener(this));
        server.getEventManager().register(this, new VelocityConnectionListener(this));

        logger.info("============================kAuth============================");
        logger.info("kAuth Velocity Senkronizasyon v1.0.1");
        logger.info("Kanal: " + MessageConstants.CHANNEL);
        logger.info("kAuth Velocity aktif!");
        logger.info("============================kAuth============================");
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
