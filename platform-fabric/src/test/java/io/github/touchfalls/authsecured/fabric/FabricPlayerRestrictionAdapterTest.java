package io.github.touchfalls.authsecured.fabric;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FabricPlayerRestrictionAdapterTest {

    @Test
    void tracksAuthenticationLifecycle() {
        FabricPlayerRestrictionAdapter adapter = new FabricPlayerRestrictionAdapter();
        UUID uuid = UUID.randomUUID();

        assertFalse(adapter.isAuthenticated(uuid));
        adapter.setPlayerUnauthenticated(uuid, null);
        assertEquals(AuthState.AUTH_REQUIRED, adapter.getAuthState(uuid));
        adapter.setPlayerAuthenticated(uuid);
        assertTrue(adapter.isAuthenticated(uuid));
        adapter.removePlayer(uuid);
        assertFalse(adapter.isAuthenticated(uuid));
    }

    @Test
    void listsOnlyAuthenticatedPlayers() {
        FabricPlayerRestrictionAdapter adapter = new FabricPlayerRestrictionAdapter();
        UUID authenticated = UUID.randomUUID();
        UUID pending = UUID.randomUUID();

        adapter.setPlayerUnauthenticated(authenticated, null);
        adapter.setPlayerUnauthenticated(pending, null);
        adapter.setPlayerAuthenticated(authenticated);

        assertEquals(Set.of(authenticated), adapter.getAuthenticatedPlayers());
    }
}
