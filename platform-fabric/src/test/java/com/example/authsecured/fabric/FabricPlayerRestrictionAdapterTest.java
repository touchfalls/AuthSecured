package com.example.authsecured.fabric;

import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.fabric.adapter.FabricPlayerRestrictionAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FabricPlayerRestrictionAdapterTest {

    private FabricPlayerRestrictionAdapter adapter;
    private UUID playerUuid;

    @BeforeEach
    void setUp() {
        adapter = new FabricPlayerRestrictionAdapter();
        playerUuid = UUID.randomUUID();
    }

    @Test
    @DisplayName("Test player unauthenticated, authenticated, and removal lifecycle")
    void testPlayerRestrictionLifecycle() {
        // Initial state
        assertFalse(adapter.isAuthenticated(playerUuid));
        assertEquals(AuthState.AUTH_REQUIRED, adapter.getAuthState(playerUuid));

        // Mark unauthenticated
        adapter.setPlayerUnauthenticated(playerUuid, null);
        assertFalse(adapter.isAuthenticated(playerUuid));
        assertEquals(AuthState.AUTH_REQUIRED, adapter.getAuthState(playerUuid));

        // Mark authenticated
        adapter.setPlayerAuthenticated(playerUuid);
        assertTrue(adapter.isAuthenticated(playerUuid));
        assertEquals(AuthState.AUTHENTICATED, adapter.getAuthState(playerUuid));

        // Remove player on disconnect
        adapter.removePlayer(playerUuid);
        assertFalse(adapter.isAuthenticated(playerUuid));
        assertEquals(AuthState.AUTH_REQUIRED, adapter.getAuthState(playerUuid));
    }
}
