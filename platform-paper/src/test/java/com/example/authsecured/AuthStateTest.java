package com.example.authsecured;

import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AuthStateTest {

    @Test
    void testAuthStateTransition() {
        PlayerRestrictionManager manager = new PlayerRestrictionManager();
        UUID uuid = UUID.randomUUID();

        assertEquals(AuthState.AUTH_REQUIRED, manager.getAuthState(uuid));
        assertFalse(manager.isAuthenticated(uuid));

        manager.setPlayerAuthenticated(uuid);
        assertEquals(AuthState.AUTHENTICATED, manager.getAuthState(uuid));
        assertTrue(manager.isAuthenticated(uuid));

        manager.setPlayerUnauthenticated(uuid, null);
        assertEquals(AuthState.AUTH_REQUIRED, manager.getAuthState(uuid));
        assertFalse(manager.isAuthenticated(uuid));
    }
}
