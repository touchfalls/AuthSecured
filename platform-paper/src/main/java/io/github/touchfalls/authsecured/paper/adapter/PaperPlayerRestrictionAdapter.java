package io.github.touchfalls.authsecured.paper.adapter;

import io.github.touchfalls.authsecured.domain.auth.AuthState;
import io.github.touchfalls.authsecured.paper.player.PlayerRestrictionManager;
import io.github.touchfalls.authsecured.ports.PlayerRestrictionAdapter;
import org.bukkit.Location;

import java.util.Set;
import java.util.UUID;

public class PaperPlayerRestrictionAdapter implements PlayerRestrictionAdapter {

    private final PlayerRestrictionManager restrictionManager;

    public PaperPlayerRestrictionAdapter(PlayerRestrictionManager restrictionManager) {
        this.restrictionManager = restrictionManager;
    }

    @Override
    public void setPlayerAuthenticated(UUID playerUuid) {
        restrictionManager.setPlayerAuthenticated(playerUuid);
    }

    @Override
    public void setPlayerUnauthenticated(UUID playerUuid, Object location) {
        Location loc = (location instanceof Location) ? (Location) location : null;
        restrictionManager.setPlayerUnauthenticated(playerUuid, loc);
    }

    @Override
    public void setPasswordChangeRequired(UUID playerUuid) {
        restrictionManager.setPasswordChangeRequired(playerUuid);
    }

    @Override
    public void removePlayer(UUID playerUuid) {
        restrictionManager.removePlayer(playerUuid);
    }

    @Override
    public boolean isAuthenticated(UUID playerUuid) {
        return restrictionManager.isAuthenticated(playerUuid);
    }

    @Override
    public AuthState getAuthState(UUID playerUuid) {
        return restrictionManager.getAuthState(playerUuid);
    }

    @Override
    public Set<UUID> getAuthenticatedPlayers() {
        return restrictionManager.getAuthenticatedPlayers();
    }
}
