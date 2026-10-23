package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
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

    @Test
    void temporaryPasswordStateStaysRestrictedAndMovementLockFollowsState() {
        PlayerRestrictionManager manager = new PlayerRestrictionManager();
        java.util.List<String> calls = new java.util.ArrayList<>();
        manager.setMovementLock(new PlayerRestrictionManager.MovementLock() {
            @Override public void lock(UUID playerUuid) { calls.add("lock"); }
            @Override public void unlock(UUID playerUuid) { calls.add("unlock"); }
        });
        UUID uuid = UUID.randomUUID();

        manager.setPlayerUnauthenticated(uuid, null);
        manager.setPasswordChangeRequired(uuid);
        assertEquals(AuthState.PASSWORD_CHANGE_REQUIRED, manager.getAuthState(uuid));
        assertFalse(manager.isAuthenticated(uuid));

        manager.setPlayerAuthenticated(uuid);
        manager.setPasswordChangeRequired(uuid);
        assertEquals(AuthState.AUTHENTICATED, manager.getAuthState(uuid));

        manager.removePlayer(uuid);
        assertEquals(java.util.List.of("lock", "unlock", "unlock"), calls);
    }

    @Test
    void authenticatedPlayerSnapshotFollowsState() {
        PlayerRestrictionManager manager = new PlayerRestrictionManager();
        UUID authenticated = UUID.randomUUID();
        UUID pending = UUID.randomUUID();

        manager.setPlayerUnauthenticated(pending, null);
        manager.setPlayerAuthenticated(authenticated);
        assertEquals(java.util.Set.of(authenticated), manager.getAuthenticatedPlayers());

        manager.removePlayer(authenticated);
        assertTrue(manager.getAuthenticatedPlayers().isEmpty());
    }
}
