package io.github.touchfalls.authsecured.util;

import java.util.UUID;

/** Decides whether a joining player's identity was verified by Mojang, so no password is needed. */
public final class PremiumIdentity {

    private PremiumIdentity() { }

    /**
     * An online-mode server verifies every player itself. An offline-mode backend behind an online-mode
     * proxy never sees that check, but the proxy forwards the real Mojang profile: a random (version 4)
     * UUID. A player who connects to the backend directly always gets the name-based version 3 UUID, so
     * the forwarded one cannot be forged unless the forwarding itself is exposed. Hence
     * {@code trustProxyUuid} must only be on when the backend accepts connections from the proxy alone and
     * uses secure forwarding (for example Velocity modern forwarding). Version 4 exactly: Floodgate and
     * other tools use different versions.
     */
    public static boolean isVerified(UUID playerUuid, boolean serverOnlineMode, boolean trustProxyUuid) {
        return serverOnlineMode || (trustProxyUuid && playerUuid != null && playerUuid.version() == 4);
    }
}
