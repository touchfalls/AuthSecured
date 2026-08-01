package com.example.authsecured.fabric.adapter;

import com.example.authsecured.domain.auth.AuthState;
import com.example.authsecured.ports.PlayerRestrictionAdapter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FabricPlayerRestrictionAdapter implements PlayerRestrictionAdapter {

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
    public void removePlayer(UUID playerUuid) {
        playerStates.remove(playerUuid);
    }

    @Override
    public boolean isAuthenticated(UUID playerUuid) {
        return playerStates.getOrDefault(playerUuid, AuthState.AUTH_REQUIRED) == AuthState.AUTHENTICATED;
    }

    @Override
    public AuthState getAuthState(UUID playerUuid) {
        return playerStates.getOrDefault(playerUuid, AuthState.AUTH_REQUIRED);
    }
}
