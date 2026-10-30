package io.github.touchfalls.authsecured.fabric;

import io.github.touchfalls.authsecured.bootstrap.DependencyContainer;
import io.github.touchfalls.authsecured.fabric.adapter.FabricAuthPlatform;
import io.github.touchfalls.authsecured.fabric.adapter.FabricLogger;
import io.github.touchfalls.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import io.github.touchfalls.authsecured.fabric.command.FabricCommandRegistration;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthSecuredFabricMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AuthSecured");
    private static volatile AuthSecuredFabricMod instance;

    private final FabricPlayerRestrictionAdapter restrictionAdapter = new FabricPlayerRestrictionAdapter();
    private final Map<UUID, String> playerAddresses = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> connectionIds = new ConcurrentHashMap<>();
    private FabricAuthPlatform platform;
    private DependencyContainer container;
    private volatile boolean initialized;
    private int timeoutTickCounter;

    @Override
    public void onInitializeServer() {
        instance = this;
        this.platform = new FabricAuthPlatform(new FabricLogger(LOGGER), restrictionAdapter);
        this.platform.setAddressSource(playerAddresses::values);

        FabricCommandRegistration.register(this);
        registerLifecycleEvents();
        registerConnectionEvents();
        registerRestrictionEvents();
        registerAuthenticationTimeout();
        LOGGER.info("AuthSecured Fabric adapter registered; core initialization will run during server startup.");
    }

    private void registerLifecycleEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            platform.setServer(server);
            try {
                this.container = new DependencyContainer(platform);
                this.container.init();
                this.initialized = true;
                LOGGER.info("AuthSecured initialized for Fabric at {}",
                        platform.getDataFolder().getAbsolutePath());
            } catch (Exception | LinkageError exception) {
                initialized = false;
                if (container != null) {
                    try {
                        container.close();
                    } catch (RuntimeException closeFailure) {
                        LOGGER.warn("AuthSecured cleanup after a failed start also failed");
                    }
                    container = null;
                }
                LOGGER.error("AuthSecured failed to initialize; players will remain restricted", exception);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            initialized = false;
            if (container != null) {
                container.close();
                container = null;
            }
            playerAddresses.clear();
            connectionIds.clear();
            LOGGER.info("AuthSecured Fabric adapter stopped.");
        });
    }

    private void registerConnectionEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID uuid = player.getUUID();
            String address = extractAddress(handler.getRemoteAddress());
            UUID connectionId = UUID.randomUUID();
            connectionIds.put(uuid, connectionId);
            if (address == null) playerAddresses.remove(uuid);
            else playerAddresses.put(uuid, address);
            restrictionAdapter.setPlayerUnauthenticated(uuid, null);
            if (player.isPassenger() && isRestricted(uuid, "restrictions.freeze-player", true)) {
                player.stopRiding();
            }

            if (!initialized) {
                // No timeout runs without a container, so a frozen player would otherwise stay forever.
                platform.disconnectPlayer(uuid, message("messages.initialization-failed",
                        "§cAuthSecured is not initialized."));
                return;
            }

            ConfigManager config = container.getConfigManager();
            container.getAuthenticationTimeoutService().start(uuid);
            if (platform.isOnlineMode() && config.getBoolean("auth.online-mode.bypass-password", true)) {
                restrictionAdapter.setPlayerAuthenticated(uuid);
                container.getAuthenticationTimeoutService().clear(uuid);
                player.sendSystemMessage(Component.literal(config.getMessage("messages.auth-bypass",
                        "§aOnline-mode active: automatically authenticated!")));
                return;
            }

            byte[] ipHash = container.getIpHashService().hashIp(address);
            container.getSessionService().isValidActiveSession(uuid, ipHash)
                    .thenCompose(valid -> {
                        if (valid) {
                            runForCurrentConnection(uuid, connectionId, () -> {
                                restrictionAdapter.setPlayerAuthenticated(uuid);
                                container.getAuthenticationTimeoutService().clear(uuid);
                                platform.sendMessage(uuid, config.getMessage("messages.session-restored",
                                        "§aSession restored automatically!"));
                            });
                            return java.util.concurrent.CompletableFuture.completedFuture(null);
                        }
                        return container.getAuthService().isRegistered(uuid).thenAccept(registered ->
                                runForCurrentConnection(uuid, connectionId, () -> platform.sendMessage(uuid, config.getMessage(
                                        registered ? "messages.login-prompt" : "messages.register-prompt",
                                        registered
                                                ? "§ePlease log in using /login <password>"
                                                : "§ePlease register using /register <password> <confirmPassword>"))));
                    })
                    .exceptionally(throwable -> {
                        LOGGER.error("Failed to establish authentication state for {}", player.getName().getString(), throwable);
                        return null;
                    });
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUUID();
            boolean authenticated = restrictionAdapter.isAuthenticated(uuid);
            DependencyContainer currentContainer = container;
            if (currentContainer != null) {
                currentContainer.getAuthenticationTimeoutService().clear(uuid);
                if (initialized && authenticated) {
                    // With session.extend-while-online the session lifetime now starts at disconnect.
                    currentContainer.getSessionService().extendActiveSessions(List.of(uuid))
                            .exceptionally(throwable -> {
                                LOGGER.error("Session extension failed for {}", handler.getPlayer().getName().getString());
                                return null;
                            });
                }
            }
            restrictionAdapter.removePlayer(uuid);
            playerAddresses.remove(uuid);
            connectionIds.remove(uuid);
        });
    }

    private void registerAuthenticationTimeout() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!initialized || ++timeoutTickCounter < 20) return;
            timeoutTickCounter = 0;
            container.getMaintenanceService().runIfDue(System.currentTimeMillis());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (!restrictionAdapter.isAuthenticated(player.getUUID())) {
                    container.getAuthenticationTimeoutService().ensureStarted(player.getUUID());
                }
            }
            for (UUID uuid : container.getAuthenticationTimeoutService().drainExpired(Instant.now())) {
                if (!restrictionAdapter.isAuthenticated(uuid) && platform.isPlayerOnline(uuid)) {
                    container.getSecurityService().logSecurityEvent(
                            null,
                            io.github.touchfalls.authsecured.domain.security.SecurityEventType.AUTH_TIMEOUT,
                            null,
                            "{\"reason\":\"authentication_deadline\"}"
                    );
                    platform.disconnectPlayer(uuid, container.getConfigManager().getMessage(
                            "messages.authentication-timeout-kick",
                            "§cAuthentication time expired. Reconnect and try again."
                    ));
                }
            }
        });
    }

    private void registerRestrictionEvents() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (isRestricted(sender.getUUID(), "restrictions.block-chat", true)) {
                sender.sendSystemMessage(Component.literal(message("messages.must-auth",
                        "§cYou must authenticate before doing this!")));
                return false;
            }
            return true;
        });

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player
                    && isRestricted(player.getUUID(), "restrictions.block-damage-taken", true)) {
                return false;
            }
            if (source.getEntity() instanceof ServerPlayer player
                    && isRestricted(player.getUUID(), "restrictions.block-damage-dealt", true)) {
                return false;
            }
            return true;
        });
    }

    private static String extractAddress(SocketAddress socketAddress) {
        if (socketAddress instanceof InetSocketAddress inetAddress) {
            return inetAddress.getAddress() != null ? inetAddress.getAddress().getHostAddress() : null;
        }
        return null;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public DependencyContainer getContainer() {
        return container;
    }

    public FabricPlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    public String getPlayerAddress(UUID uuid) {
        return playerAddresses.get(uuid);
    }

    public boolean isRestricted(UUID uuid, String configPath, boolean defaultValue) {
        if (restrictionAdapter.isAuthenticated(uuid)) {
            return false;
        }
        DependencyContainer currentContainer = container;
        return !initialized || currentContainer == null
                ? defaultValue
                : currentContainer.getConfigManager().getBoolean(configPath, defaultValue);
    }

    public boolean isAllowedCommand(String command) {
        if (!initialized) {
            return false;
        }
        String normalized = command.startsWith("/") ? command : "/" + command;
        normalized = normalized.strip().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        List<String> defaults = List.of("/login", "/l", "/register", "/reg", "/changepassword", "/cp");
        return container.getConfigManager().getStringList("restrictions.allowed-commands", defaults)
                .stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    /** True when the player has not authenticated and the command is outside the pre-login allow-list. */
    public boolean isCommandBlocked(UUID uuid, String command) {
        return isRestricted(uuid, "restrictions.block-commands", true)
                && (command == null || !isAllowedCommand(command));
    }

    public boolean shouldBlockFlight(UUID uuid) {
        if (restrictionAdapter.isAuthenticated(uuid)) {
            return false;
        }
        DependencyContainer currentContainer = container;
        return !initialized || currentContainer == null
                || !currentContainer.getConfigManager().getBoolean("restrictions.allow-flight", false);
    }

    public String message(String key, String fallback) {
        DependencyContainer currentContainer = container;
        return currentContainer != null
                ? currentContainer.getConfigManager().getMessage(key, fallback)
                : fallback;
    }

    private void runForCurrentConnection(UUID uuid, UUID connectionId, Runnable task) {
        platform.runTaskOnMainThread(() -> {
            if (platform.isPlayerOnline(uuid)
                    && connectionId.equals(connectionIds.get(uuid))) {
                task.run();
            }
        });
    }

    public static AuthSecuredFabricMod getInstance() {
        return instance;
    }
}
