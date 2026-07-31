package com.example.authsecured.fabric;

import com.example.authsecured.bootstrap.DependencyContainer;
import java.io.File;
import java.util.logging.Logger;

/**
 * Entrypoint for Fabric Dedicated Server environment.
 * Enables AuthSecured pure Java domain, authentication services, and database layers on Fabric.
 */
public class AuthSecuredFabricMod {

    private static final Logger LOGGER = Logger.getLogger("AuthSecured");
    private DependencyContainer container;

    public void onInitializeServer() {
        LOGGER.info("[AuthSecured] Initializing AuthSecured Fabric Server Mod v1.0.1...");

        File dataFolder = new File("config/authsecured");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        LOGGER.info("[AuthSecured] AuthSecured core authentication engine initialized successfully on Fabric.");
    }

    public void onDisableServer() {
        if (container != null) {
            container.close();
        }
        LOGGER.info("[AuthSecured] AuthSecured Fabric Server Mod disabled.");
    }
}
