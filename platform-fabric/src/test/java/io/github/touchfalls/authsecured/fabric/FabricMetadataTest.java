package io.github.touchfalls.authsecured.fabric;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FabricMetadataTest {
    @Test
    void minecraftJavaMixinAndBytecodeTargetsAgree() throws Exception {
        String minecraft = System.getProperty("authsecured.minecraftVersion");
        int javaRelease = minecraft.startsWith("26.") ? 25 : 21;
        String metadata;
        String mixins;
        // Inside Fabric Loader every mod on the class path has a fabric.mod.json; pick ours.
        metadata = java.util.Collections.list(getClass().getClassLoader().getResources("fabric.mod.json")).stream()
                .map(url -> {
                    try (var in = url.openStream()) {
                        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .filter(json -> json.contains("\"id\": \"authsecured\""))
                .findFirst().orElseThrow();
        try (var mixin = getClass().getClassLoader().getResourceAsStream("authsecured.mixins.json")) {
            assertNotNull(mixin);
            mixins = new String(mixin.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(metadata.contains("\"minecraft\": \"=" + minecraft + "\""));
        assertTrue(metadata.contains("\"java\": \">=" + javaRelease + "\""));
        assertTrue(metadata.contains("io.github.touchfalls.authsecured.fabric.AuthSecuredFabricMod"));
        assertTrue(mixins.contains("\"compatibilityLevel\": \"JAVA_" + javaRelease + "\""));
        assertTrue(mixins.contains("io.github.touchfalls.authsecured.fabric.mixin"));

        String classPath = AuthSecuredFabricMod.class.getName().replace('.', '/') + ".class";
        try (var stream = new DataInputStream(getClass().getClassLoader().getResourceAsStream(classPath))) {
            assertEquals(0xCAFEBABE, stream.readInt());
            stream.readUnsignedShort();
            assertEquals(javaRelease + 44, stream.readUnsignedShort());
        }
    }
}
