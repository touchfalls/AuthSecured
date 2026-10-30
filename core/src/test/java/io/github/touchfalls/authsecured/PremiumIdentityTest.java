package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.util.PremiumIdentity;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PremiumIdentityTest {

    private static final UUID MOJANG = UUID.randomUUID();
    private static final UUID OFFLINE = UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
    private static final UUID FLOODGATE = new UUID(0L, 123456789L);

    @Test
    void onlineModeServersAlwaysVerify() {
        assertTrue(PremiumIdentity.isVerified(OFFLINE, true, false));
    }

    @Test
    void forwardedMojangUuidsAreTrustedOnlyWhenEnabled() {
        assertFalse(PremiumIdentity.isVerified(MOJANG, false, false));
        assertTrue(PremiumIdentity.isVerified(MOJANG, false, true));
    }

    @Test
    void offlineAndBedrockUuidsAreNeverTrusted() {
        assertFalse(PremiumIdentity.isVerified(OFFLINE, false, true));
        assertFalse(PremiumIdentity.isVerified(FLOODGATE, false, true));
        assertFalse(PremiumIdentity.isVerified(null, false, true));
    }
}
