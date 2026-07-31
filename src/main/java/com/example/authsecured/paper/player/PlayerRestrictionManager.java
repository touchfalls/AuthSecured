package com.example.authsecured.paper.player;

import com.example.authsecured.domain.auth.AuthState;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRestrictionManager {

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private final Map<UUID, AuthState> playerStates = new ConcurrentHashMap<>();

    public void setPlayerAuthenticated(UUID playerUuid) {
        authenticatedPlayers.add(playerUuid);
        initialLocations.remove(playerUuid);
        playerStates.put(playerUuid, AuthState.AUTHENTICATED);
    }

    public void setPlayerUnauthenticated(UUID playerUuid, Location currentLocation) {
        authenticatedPlayers.remove(playerUuid);
        playerStates.put(playerUuid, AuthState.AUTH_REQUIRED);
        if (currentLocation != null) {
            initialLocations.putIfAbsent(playerUuid, currentLocation.clone());
        }
    }

    public void removePlayer(UUID playerUuid) {
        authenticatedPlayers.remove(playerUuid);
        initialLocations.remove(playerUuid);
        playerStates.remove(playerUuid);
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return authenticatedPlayers.contains(playerUuid);
    }

    public Location getInitialLocation(UUID playerUuid) {
        return initialLocations.get(playerUuid);
    }

    public AuthState getAuthState(UUID playerUuid) {
        return playerStates.getOrDefault(playerUuid, AuthState.AUTH_REQUIRED);
    }
}
