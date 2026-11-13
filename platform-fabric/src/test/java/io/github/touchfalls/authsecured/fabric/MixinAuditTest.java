package io.github.touchfalls.authsecured.fabric;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.MixinEnvironment;

class MixinAuditTest {

    /**
     * Compiling a mixin does not prove its targets exist in this Minecraft version. The audit loads
     * every target class and applies the mixins, and with defaultRequire 1 a missing handler fails it.
     * Without this the first sign would be a server that crashes, or lets commands through, at runtime.
     */
    @Test
    void everyMixinAppliesToThisMinecraftVersion() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        MixinEnvironment.getCurrentEnvironment().audit();
    }
}
