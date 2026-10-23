package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.ports.SessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    @Test
    void testSessionCreationAndRevocation() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.save(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        Mockito.when(repository.revoke(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));

        SessionService sessionService = new SessionService(repository, true, 30);
        UUID playerUuid = UUID.randomUUID();

        Session session = sessionService.createSession(1L, playerUuid, "server1").join();
        assertNotNull(session);
        assertEquals(playerUuid, session.getPlayerUuid());

        sessionService.revokeSession(session.getId()).join();
        Mockito.verify(repository).revoke(session.getId());
    }

    @Test
    void verifiesIpHashForPersistentSessions() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        UUID playerUuid = UUID.randomUUID();
        byte[] expectedIpHash = {1, 2, 3};
        Session session = Session.create(1L, playerUuid, expectedIpHash, "server1", 60);
        Mockito.when(repository.findActiveByPlayerUuid(playerUuid))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(session)));

        SessionService service = new SessionService(repository, true, true, 60, true);

        assertTrue(service.isValidActiveSession(playerUuid, expectedIpHash).join());
        assertFalse(service.isValidActiveSession(playerUuid, new byte[]{9, 9, 9}).join());
    }

    @Test
    void missingIpDoesNotPersistAnIpBoundSession() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.findActiveByPlayerUuid(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.empty()));
        SessionService service = new SessionService(repository, true, true, 60, true);
        UUID uuid = UUID.randomUUID();
        service.createSession(1L, uuid, null, "server").join();
        assertFalse(service.isValidActiveSession(uuid, null).join());
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void nonPersistentSessionsStayInMemory() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.deleteAll()).thenReturn(CompletableFuture.completedFuture(null));
        UUID playerUuid = UUID.randomUUID();
        byte[] ipHash = {4, 5, 6};
        SessionService service = new SessionService(repository, true, false, 60, true);

        service.createSession(1L, playerUuid, ipHash, "server1").join();

        assertTrue(service.isValidActiveSession(playerUuid, ipHash).join());
        Mockito.verify(repository).deleteAll();
        Mockito.verifyNoMoreInteractions(repository);
    }

    @Test
    void disabledSessionsNeverRestore() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        SessionService service = new SessionService(repository, false, true, 60, false);

        assertFalse(service.isValidActiveSession(UUID.randomUUID(), null).join());
        Mockito.verifyNoInteractions(repository);
    }

    @Test
    void logoutRevokesEveryPersistentSessionForTheAccountNotJustTheNewestOne() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        UUID playerUuid = UUID.randomUUID();
        Long accountId = 7L;
        // The repository's "active" lookup returns only the newest session, but the player
        // may also have an older one still valid (e.g. from a different IP) that must also end.
        Session newest = Session.create(accountId, playerUuid, "server1", 60);
        Mockito.when(repository.findActiveByPlayerUuid(playerUuid))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.of(newest)));
        Mockito.when(repository.revokeAllForAccount(accountId)).thenReturn(CompletableFuture.completedFuture(null));

        SessionService service = new SessionService(repository, true, 30);
        service.revokeActiveSessionForPlayer(playerUuid).join();

        Mockito.verify(repository).revokeAllForAccount(accountId);
        Mockito.verify(repository, Mockito.never()).revoke(Mockito.any());
    }

    @Test
    void logoutWithNoActiveSessionDoesNothing() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        UUID playerUuid = UUID.randomUUID();
        Mockito.when(repository.findActiveByPlayerUuid(playerUuid))
                .thenReturn(CompletableFuture.completedFuture(java.util.Optional.empty()));

        SessionService service = new SessionService(repository, true, 30);
        service.revokeActiveSessionForPlayer(playerUuid).join();

        Mockito.verify(repository, Mockito.never()).revokeAllForAccount(Mockito.any());
        Mockito.verify(repository, Mockito.never()).revoke(Mockito.any());
    }

    @Test
    void runtimeTimeoutAffectsNewSessionsImmediately() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.save(Mockito.any())).thenReturn(CompletableFuture.completedFuture(null));
        SessionService service = new SessionService(repository, true, true, 30, false);

        service.setTimeoutSeconds(120);
        Session session = service.createSession(1L, UUID.randomUUID(), "server1").join();

        assertEquals(120, service.getTimeoutSeconds());
        assertEquals(120, java.time.Duration.between(session.getCreatedAt(), session.getExpiresAt()).getSeconds());
        assertThrows(IllegalArgumentException.class, () -> service.setTimeoutSeconds(0));
        assertEquals(120, service.getTimeoutSeconds());
    }

    @Test
    void extendingPersistentSessionsMovesExpiryToNowPlusTimeout() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.extendActive(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        SessionService service = new SessionService(repository, true, true, 600, true);
        service.setExtendWhileOnline(true);
        UUID playerUuid = UUID.randomUUID();

        Instant before = Instant.now();
        service.extendActiveSessions(List.of(playerUuid)).join();

        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        Mockito.verify(repository).extendActive(Mockito.eq(List.of(playerUuid)), expiry.capture());
        assertFalse(expiry.getValue().isBefore(before.plusSeconds(600)));
        assertTrue(expiry.getValue().isBefore(Instant.now().plusSeconds(601)));
    }

    @Test
    void sessionsAreNotExtendedUnlessEnabled() {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        SessionService service = new SessionService(repository, true, true, 600, true);

        service.extendActiveSessions(List.of(UUID.randomUUID())).join();
        service.setExtendWhileOnline(true);
        service.extendActiveSessions(List.of()).join();

        Mockito.verify(repository, Mockito.never()).extendActive(Mockito.any(), Mockito.any());
    }

    @Test
    void extendingInMemorySessionsKeepsThemValidPastTheirOriginalExpiry() throws InterruptedException {
        SessionRepository repository = Mockito.mock(SessionRepository.class);
        Mockito.when(repository.deleteAll()).thenReturn(CompletableFuture.completedFuture(null));
        SessionService service = new SessionService(repository, true, false, 1, false);
        service.setExtendWhileOnline(true);
        UUID playerUuid = UUID.randomUUID();
        Session original = service.createSession(1L, playerUuid, null, "server1").join();

        service.setTimeoutSeconds(3_600);
        service.extendActiveSessions(List.of(playerUuid)).join();
        Thread.sleep(1_100);
        assertTrue(service.isValidActiveSession(playerUuid).join());

        // The extended copy keeps the session ID, so revoking it still ends the session.
        service.revokeSession(original.getId()).join();
        assertFalse(service.isValidActiveSession(playerUuid).join());
        Mockito.verify(repository, Mockito.never()).extendActive(Mockito.any(), Mockito.any());
    }
}
