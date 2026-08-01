package com.example.authsecured.fabric;

import com.example.authsecured.bootstrap.DependencyContainer;
import net.fabricmc.api.DedicatedServerModInitializer;

import java.io.File;
import java.util.logging.Logger;

/**
 * Entrypoint for Fabric Dedicated Server environment.
 * Enables AuthSecured pure Java domain, authentication services, and database layers on Fabric.
 */
public class AuthSecuredFabricMod implements DedicatedServerModInitializer {

    private static final Logger LOGGER = Logger.getLogger("AuthSecured");
    private DependencyContainer container;

    @Override
    public void onInitializeServer() {
        LOGGER.info("[AuthSecured] Initializing AuthSecured Fabric Server Mod v1.0.2...");

        File dataFolder = new File("config/authsecured");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        try {
            this.container = new DependencyContainer(dataFolder, LOGGER);
            this.container.init();
            LOGGER.info("[AuthSecured] AuthSecured core authentication engine initialized successfully on Fabric.");
        } catch (Exception e) {
            LOGGER.severe("[AuthSecured] Failed to initialize AuthSecured Fabric Server Mod: " + e.getMessage());
        }
    }

    public void onDisableServer() {
        if (container != null) {
            container.close();
        }
        LOGGER.info("[AuthSecured] AuthSecured Fabric Server Mod disabled.");
    }

    public DependencyContainer getContainer() {
        return container;
    }
}
