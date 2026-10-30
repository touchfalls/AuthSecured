package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.domain.session.Session;
import io.github.touchfalls.authsecured.infrastructure.security.Argon2PasswordHasher;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordRehashTest {

    private static final char[] PASSWORD = "correct-password".toCharArray();

    @Test
    void hashFromOtherCostSettingsNeedsRehash() {
        Argon2PasswordHasher current = new Argon2PasswordHasher(2048, 1, 1, 16, 16);
        String ownHash = current.hash(PASSWORD);

        assertFalse(current.needsRehash(ownHash));
        assertTrue(current.needsRehash(new Argon2PasswordHasher(1024, 1, 1, 16, 16).hash(PASSWORD)));
        assertTrue(current.needsRehash(new Argon2PasswordHasher(2048, 2, 1, 16, 16).hash(PASSWORD)));
        assertTrue(current.needsRehash(new Argon2PasswordHasher(2048, 1, 1, 32, 16).hash(PASSWORD)));
        assertTrue(current.needsRehash(new Argon2PasswordHasher(2048, 1, 1, 16, 32).hash(PASSWORD)));
        // Malformed or foreign hashes are left to verify(), which rejects them.
        assertFalse(current.needsRehash("$2a$10$notArgon"));
        assertFalse(current.needsRehash(ownHash.replace("v=19", "v=16")));
        assertFalse(current.needsRehash(null));
    }

    @Test
    void successfulLoginReplacesAnOutdatedHashWithACurrentOne() {
        Argon2PasswordHasher current = new Argon2PasswordHasher(2048, 1, 1, 16, 16);
        String outdatedHash = new Argon2PasswordHasher(1024, 1, 1, 16, 16).hash(PASSWORD);
        AccountRepository accounts = accountWith(outdatedHash, false);
        when(accounts.replacePasswordHash(any(), eq(outdatedHash), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        assertEquals(AuthResult.SUCCESS, login(accounts, current));

        ArgumentCaptor<String> newHash = ArgumentCaptor.forClass(String.class);
        verify(accounts, timeout(5_000)).replacePasswordHash(any(), eq(outdatedHash), newHash.capture(), any());
        assertTrue(current.verify(PASSWORD, newHash.getValue()));
        assertFalse(current.needsRehash(newHash.getValue()));
    }

    @Test
    void currentHashIsLeftAlone() {
        Argon2PasswordHasher current = new Argon2PasswordHasher(2048, 1, 1, 16, 16);
        AccountRepository accounts = accountWith(current.hash(PASSWORD), false);

        assertEquals(AuthResult.SUCCESS, login(accounts, current));

        verify(accounts, after(300).never()).replacePasswordHash(any(), any(), any(), any());
    }

    @Test
    void temporaryPasswordIsNotRehashedBecauseItIsReplacedAnyway() {
        Argon2PasswordHasher current = new Argon2PasswordHasher(2048, 1, 1, 16, 16);
        AccountRepository accounts = accountWith(new Argon2PasswordHasher(1024, 1, 1, 16, 16).hash(PASSWORD), true);

        assertEquals(AuthResult.PASSWORD_CHANGE_REQUIRED, login(accounts, current));

        verify(accounts, after(300).never()).replacePasswordHash(any(), any(), any(), any());
    }

    private static AuthResult login(AccountRepository accounts, Argon2PasswordHasher hasher) {
        PasswordService passwords = new PasswordService(hasher, 8, 128, 2);
        try {
            LoginService service = new LoginService(accounts, passwords, permissiveLimits(),
                    sessions(), auditSink(), 5, 900);
            return service.login(UUID.randomUUID(), "Player", PASSWORD.clone(), new byte[]{1}).join();
        } finally {
            passwords.shutdown();
        }
    }

    private static AccountRepository accountWith(String passwordHash, boolean temporary) {
        AccountRepository accounts = mock(AccountRepository.class);
        Account account = Account.createNew(UUID.randomUUID(), "Player", "player", passwordHash);
        if (temporary) account.setTemporaryPassword(passwordHash, Instant.now().plusSeconds(300));
        when(accounts.findByUuid(any())).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.resetFailedAttemptsAtomic(any(), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.exchangeLastLoginIpHash(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        return accounts;
    }

    private static SessionService sessions() {
        SessionService sessions = mock(SessionService.class);
        when(sessions.createSession(any(), any(), any(), anyString())).thenAnswer(invocation ->
                CompletableFuture.completedFuture(Session.create(1L, UUID.randomUUID(), "server", 60)));
        return sessions;
    }

    private static RateLimitService permissiveLimits() {
        RateLimitService limits = mock(RateLimitService.class);
        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.isAccountRateLimited(anyString())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.recordSuccessfulLogin(anyString())).thenReturn(CompletableFuture.completedFuture(null));
        return limits;
    }

    private static SecurityService auditSink() {
        SecurityService security = mock(SecurityService.class);
        when(security.logLoginAttempt(any(), any(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(security.logSecurityEvent(any(), any(SecurityEventType.class), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        return security;
    }
}
