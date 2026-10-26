package io.github.touchfalls.authsecured.paper.player;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import org.bukkit.Location;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PlayerRestrictionManager {

    /** Applies and removes client-side movement locks; a no-op outside a running server. */
    public interface MovementLock {
        MovementLock NONE = new MovementLock() {
            @Override public void lock(UUID playerUuid) { }
            @Override public void unlock(UUID playerUuid) { }
        };

        void lock(UUID playerUuid);

        void unlock(UUID playerUuid);
    }

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> initialLocations = new ConcurrentHashMap<>();
    private final Map<UUID, AuthState> playerStates = new ConcurrentHashMap<>();
    private volatile MovementLock movementLock = MovementLock.NONE;
    private volatile Consumer<UUID> commandListRefresher = playerUuid -> { };

    public void setMovementLock(MovementLock movementLock) {
        this.movementLock = movementLock == null ? MovementLock.NONE : movementLock;
    }

    /** Called on the server thread whenever a player gains or loses authentication. */
    public void setCommandListRefresher(Consumer<UUID> commandListRefresher) {
        this.commandListRefresher = commandListRefresher == null ? playerUuid -> { } : commandListRefresher;
    }

    public void setPlayerAuthenticated(UUID playerUuid) {
        boolean changed = authenticatedPlayers.add(playerUuid);
        initialLocations.remove(playerUuid);
        playerStates.put(playerUuid, AuthState.AUTHENTICATED);
        movementLock.unlock(playerUuid);
        if (changed) commandListRefresher.accept(playerUuid);
    }

    public void setPlayerUnauthenticated(UUID playerUuid, Location currentLocation) {
        boolean changed = authenticatedPlayers.remove(playerUuid);
        playerStates.put(playerUuid, AuthState.AUTH_REQUIRED);
        if (currentLocation != null) {
            initialLocations.putIfAbsent(playerUuid, currentLocation.clone());
        }
        movementLock.lock(playerUuid);
        if (changed) commandListRefresher.accept(playerUuid);
    }

    /** Temporary password accepted: still restricted, but {@code /changepassword} may finish the login. */
    public void setPasswordChangeRequired(UUID playerUuid) {
        if (authenticatedPlayers.contains(playerUuid)) return;
        playerStates.put(playerUuid, AuthState.PASSWORD_CHANGE_REQUIRED);
    }

    public void removePlayer(UUID playerUuid) {
        authenticatedPlayers.remove(playerUuid);
        initialLocations.remove(playerUuid);
        playerStates.remove(playerUuid);
        movementLock.unlock(playerUuid);
    }

    public boolean isAuthenticated(UUID playerUuid) {
        return authenticatedPlayers.contains(playerUuid);
    }

    public Set<UUID> getAuthenticatedPlayers() {
        return Set.copyOf(authenticatedPlayers);
    }

    public Location getInitialLocation(UUID playerUuid) {
        return initialLocations.get(playerUuid);
    }

    public AuthState getAuthState(UUID playerUuid) {
        return playerStates.getOrDefault(playerUuid, AuthState.AUTH_REQUIRED);
    }
}
