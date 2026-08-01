package com.example.authsecured.fabric;

import com.example.authsecured.bootstrap.DependencyContainer;
import com.example.authsecured.fabric.adapter.FabricAuthPlatform;
import com.example.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import com.example.authsecured.fabric.command.FabricCommandRegistration;
import com.example.authsecured.ports.PlayerRestrictionAdapter;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthSecuredFabricMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("AuthSecured");
    private static AuthSecuredFabricMod instance;
    private PlayerRestrictionAdapter restrictionAdapter;
    private FabricAuthPlatform platformAdapter;
    private DependencyContainer container;

    @Override
    public void onInitializeServer() {
        instance = this;
        LOGGER.info("==================================================================");
        LOGGER.info("[AuthSecured] Initializing Fabric Dedicated Server Mod v1.0.3...");
        LOGGER.info("==================================================================");

        this.restrictionAdapter = new FabricPlayerRestrictionAdapter();
        this.platformAdapter = new FabricAuthPlatform(restrictionAdapter);
        this.container = new DependencyContainer(platformAdapter);

        // Immediate config generation and core container initialization on Fabric load
        try {
            this.container.init();
            LOGGER.info("[AuthSecured] Configuration & Core Auth Engine initialized at: " + platformAdapter.getDataFolder().getAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("[AuthSecured] CRITICAL: Failed to initialize AuthSecured container", e);
        }

        // Register Fabric Brigadier commands
        try {
            FabricCommandRegistration.registerCommands(container.getCommandExecutor());
            LOGGER.info("[AuthSecured] Registered Fabric Brigadier authentication commands (/login, /register, /changepassword, /logout, /authstatus).");
        } catch (Exception e) {
            LOGGER.error("[AuthSecured] Failed to register Fabric commands", e);
        }

        // Lifecycle Events
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            platformAdapter.setServer(server);
            LOGGER.info("[AuthSecured] Server starting event received. AuthSecured is active.");
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (container != null) {
                container.close();
            }
            LOGGER.info("[AuthSecured] AuthSecured Fabric mod stopped.");
        });

        // Player Join Event
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.getPlayer();
            restrictionAdapter.setPlayerUnauthenticated(player.getUuid(), player.getPos());
            LOGGER.info("[AuthSecured] Player joined: " + player.getName().getString() + " (" + player.getUuid() + ") - Unauthenticated.");
            String prompt = container.getConfigManager().getMessage("messages.login-prompt", "§cPlease authenticate using /login <password> or /register <password> <confirm>");
            if (prompt != null && !prompt.isBlank()) {
                player.sendMessage(Text.literal(prompt));
            }
        });

        // Player Disconnect Event
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            restrictionAdapter.removePlayer(handler.getPlayer().getUuid());
        });

        // Chat Restriction Event
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            if (!restrictionAdapter.isAuthenticated(sender.getUuid())) {
                String errorMsg = container.getConfigManager().getMessage("messages.login-required", "§cYou must log in to chat.");
                sender.sendMessage(Text.literal(errorMsg));
                return false;
            }
            return true;
        });
    }

    public static AuthSecuredFabricMod getInstance() {
        return instance;
    }

    public PlayerRestrictionAdapter getRestrictionAdapter() {
        return restrictionAdapter;
    }

    public DependencyContainer getContainer() {
        return container;
    }
}
