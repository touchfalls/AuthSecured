package io.github.touchfalls.authsecured.fabric.adapter;

import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.PlayerRestrictionAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

public final class FabricAuthPlatform implements AuthPlatform {

    private final File dataFolder;
    private final PlatformLogger logger;
    private final PlayerRestrictionAdapter restrictionAdapter;
    private volatile MinecraftServer server;

    public FabricAuthPlatform(PlatformLogger logger, PlayerRestrictionAdapter restrictionAdapter) {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("authsecured");
        this.dataFolder = path.toFile();
        this.logger = logger;
        this.restrictionAdapter = restrictionAdapter;
    }

    private volatile java.util.function.Supplier<java.util.Collection<String>> addressSource = java.util.List::of;

    /** The mod records each player's address on join; this is read from the doctor on another thread. */
    public void setAddressSource(java.util.function.Supplier<java.util.Collection<String>> addressSource) {
        this.addressSource = addressSource;
    }

    @Override
    public java.util.Collection<String> onlinePlayerAddresses() {
        return java.util.List.copyOf(addressSource.get());
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public PlatformLogger getLogger() {
        return logger;
    }

    @Override
    public PlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    @Override
    public void sendMessage(UUID playerUuid, String message) {
        MinecraftServer currentServer = server;
        if (currentServer == null || message == null || message.isBlank()) {
            return;
        }
        ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.sendSystemMessage(Component.literal(message));
        }
    }

    @Override
    public void runTaskOnMainThread(Runnable task) {
        MinecraftServer currentServer = server;
        if (currentServer == null || currentServer.isSameThread()) {
            task.run();
        } else {
            currentServer.execute(task);
        }
    }

    @Override
    public boolean isOnlineMode() {
        MinecraftServer currentServer = server;
        return currentServer != null && currentServer.usesAuthentication();
    }

    @Override
    public boolean isPlayerOnline(UUID playerUuid) {
        MinecraftServer currentServer = server;
        return currentServer != null && currentServer.getPlayerList().getPlayer(playerUuid) != null;
    }

    @Override
    public Object currentConnectionToken(UUID playerUuid) {
        // A reconnecting player gets a new ServerPlayer entity; its identity is a safe token.
        MinecraftServer currentServer = server;
        return currentServer == null ? null : currentServer.getPlayerList().getPlayer(playerUuid);
    }

    @Override
    public void disconnectPlayer(UUID playerUuid, String reason) {
        MinecraftServer currentServer = server;
        if (currentServer == null) return;
        ServerPlayer player = currentServer.getPlayerList().getPlayer(playerUuid);
        if (player != null) {
            player.connection.disconnect(Component.literal(reason));
        }
    }
}
