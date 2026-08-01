package com.example.authsecured;

import com.example.authsecured.fabric.AuthSecuredFabricMod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class FabricModInitializationTest {

    @Test
    void testFabricModInitializationLifecycle(@TempDir File tempFolder) {
        AuthSecuredFabricMod mod = new AuthSecuredFabricMod();
        assertDoesNotThrow(() -> {
            mod.onInitializeServer();
        });

        assertDoesNotThrow(() -> {
            mod.onDisableServer();
        });
    }
}
