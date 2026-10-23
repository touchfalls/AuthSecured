package io.github.touchfalls.authsecured.fabric.adapter;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.ports.PlayerRestrictionAdapter;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

public final class FabricPlayerRestrictionAdapter implements PlayerRestrictionAdapter {

    private final Map<UUID, AuthState> playerStates = new ConcurrentHashMap<>();

    @Override
    public void setPlayerAuthenticated(UUID playerUuid) {
        playerStates.put(playerUuid, AuthState.AUTHENTICATED);
    }

    @Override
    public void setPlayerUnauthenticated(UUID playerUuid, Object location) {
        playerStates.put(playerUuid, AuthState.AUTH_REQUIRED);
    }

    @Override
    public void setPasswordChangeRequired(UUID playerUuid) {
        playerStates.computeIfPresent(playerUuid, (uuid, state) ->
                state == AuthState.AUTHENTICATED ? state : AuthState.PASSWORD_CHANGE_REQUIRED);
    }

    @Override
    public void removePlayer(UUID playerUuid) {
        playerStates.remove(playerUuid);
    }

    @Override
    public boolean isAuthenticated(UUID playerUuid) {
        return getAuthState(playerUuid) == AuthState.AUTHENTICATED;
    }

    @Override
    public AuthState getAuthState(UUID playerUuid) {
        return playerStates.getOrDefault(playerUuid, AuthState.AUTH_REQUIRED);
    }

    @Override
    public Set<UUID> getAuthenticatedPlayers() {
        return playerStates.entrySet().stream()
                .filter(entry -> entry.getValue() == AuthState.AUTHENTICATED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
