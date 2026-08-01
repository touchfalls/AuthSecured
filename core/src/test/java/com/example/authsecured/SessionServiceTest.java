package com.example.authsecured;

import com.example.authsecured.application.SessionService;
import com.example.authsecured.domain.session.Session;
import com.example.authsecured.ports.SessionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

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
}
