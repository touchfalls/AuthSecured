package com.example.authsecured.fabric.adapter;

import com.example.authsecured.ports.AuthPlatform;
import com.example.authsecured.ports.PlatformLogger;
import com.example.authsecured.ports.PlayerRestrictionAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public class FabricAuthPlatform implements AuthPlatform {

    private static final Logger LOGGER = LoggerFactory.getLogger("AuthSecured");
    private final File dataFolder;
    private final PlatformLogger platformLogger;
    private final PlayerRestrictionAdapter restrictionAdapter;
    private MinecraftServer server;

    public FabricAuthPlatform(PlayerRestrictionAdapter restrictionAdapter) {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("authsecured");
        try {
            Files.createDirectories(configPath);
        } catch (Exception e) {
            LOGGER.error("Failed to create config directory: " + configPath, e);
        }
        this.dataFolder = configPath.toFile();
        this.platformLogger = new FabricLogger(LOGGER);
        this.restrictionAdapter = restrictionAdapter;
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
        return platformLogger;
    }

    @Override
    public PlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    @Override
    public void sendMessage(UUID playerUuid, String message) {
        if (message == null || message.isBlank() || server == null) return;
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerUuid);
        if (player != null) {
            player.sendMessage(Text.literal(message));
        }
    }

    @Override
    public void runTaskOnMainThread(Runnable task) {
        if (server != null) {
            server.execute(task);
        } else {
            task.run();
        }
    }

    @Override
    public boolean isOnlineMode() {
        return server != null && server.isOnlineMode();
    }
}
