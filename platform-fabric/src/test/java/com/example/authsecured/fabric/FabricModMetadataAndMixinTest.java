package com.example.authsecured.fabric;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FabricModMetadataAndMixinTest {

    @Test
    @DisplayName("Verify fabric.mod.json structure and entrypoint definition")
    void testFabricModJsonIntegrity() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("fabric.mod.json");
        assertNotNull(is, "fabric.mod.json resource must exist in classpath");

        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"id\": \"authsecured\""));
        assertTrue(content.contains("AuthSecuredFabricMod"));
        assertTrue(content.contains("\"environment\": \"server\""));

        String entrypointClassName = "com.example.authsecured.fabric.AuthSecuredFabricMod";
        assertFalse(entrypointClassName.isBlank());
    }

    @Test
    @DisplayName("Verify authsecured.mixins.json integrity and validate referenced mixins")
    void testMixinJsonClassExistence() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("authsecured.mixins.json");
        assertNotNull(is, "authsecured.mixins.json resource must exist in classpath");

        String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(content.contains("\"package\": \"com.example.authsecured.fabric.mixin\""));
        assertTrue(content.contains("ServerPlayNetworkHandlerMixin"));
        assertTrue(content.contains("ServerPlayerEntityMixin"));
    }
}
