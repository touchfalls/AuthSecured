package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {

    private static final byte[] IP = {1};

    @Test
    void passwordChangeFailureLogsOnlyCauseType() {
        PasswordService passwords = mock(PasswordService.class);
        LoginService login = mock(LoginService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(login.verifyCredentials(any(), any(), any(), any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("password=secret")));
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), mock(SecurityService.class),
                mock(AccountRepository.class), logger);
        assertEquals(AuthResult.INTERNAL_ERROR, service.changePassword(UUID.randomUUID(), "Player",
                "oldPassword12".toCharArray(), "newPassword12".toCharArray(), IP).join());
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).severe(message.capture());
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("password=secret"));
    }

    @Test
    void rejectsChangingPasswordToTheCurrentPassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        LoginService login = mock(LoginService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(login.verifyCredentials(eq(uuid), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(
                new LoginService.CredentialCheck(AuthResult.SUCCESS, account, "player")));
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), mock(SecurityService.class), accounts);
        char[] same = "samePassword12".toCharArray();

        assertEquals(AuthResult.PASSWORD_UNCHANGED,
                service.changePassword(uuid, "Player", same, same.clone(), IP).join());
        verify(passwords, never()).hashPasswordAsync(any());
        verify(accounts, never()).updatePasswordHash(any(), any(), any());
    }

    @Test
    void rejectedCredentialCheckNeverChangesThePassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        LoginService login = mock(LoginService.class);
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(login.verifyCredentials(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(
                new LoginService.CredentialCheck(AuthResult.RATE_LIMITED, null, "player")));
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), mock(SecurityService.class), accounts);

        assertEquals(AuthResult.RATE_LIMITED, service.changePassword(UUID.randomUUID(), "Player",
                "guess123456".toCharArray(), "newPassword12".toCharArray(), IP).join());
        verify(passwords, never()).hashPasswordAsync(any());
        verify(accounts, never()).updatePasswordHash(any(), any(), any());
    }

    /** End-to-end with a real LoginService: a wrong old password counts as a failed login. */
    @Test
    void wrongOldPasswordIsCountedLikeAFailedLogin() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = permissiveLimits();
        SecurityService security = auditSink();
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.verifyPasswordAsync(any(), eq("hash"))).thenReturn(CompletableFuture.completedFuture(false));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.incrementFailedAttemptsAtomic(any(), anyInt(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        LoginService login = new LoginService(accounts, passwords, limits, mock(SessionService.class),
                security, 5, 900);
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), security, accounts);

        assertEquals(AuthResult.INVALID_PASSWORD, service.changePassword(uuid, "Player",
                "guess123456".toCharArray(), "newPassword12".toCharArray(), IP).join());
        verify(accounts).incrementFailedAttemptsAtomic(any(), eq(5), eq(900L), any());
        verify(limits).recordFailedLoginAttempt("player", IP);
        verify(accounts, never()).updatePasswordHash(any(), any(), any());
    }

    @Test
    void rateLimitedPlayerCannotGuessThroughPasswordChange() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = permissiveLimits();
        when(limits.isAccountRateLimited("player")).thenReturn(CompletableFuture.completedFuture(true));
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        SecurityService security = auditSink();
        LoginService login = new LoginService(accounts, passwords, limits, mock(SessionService.class),
                security, 5, 900);
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), security, accounts);

        assertEquals(AuthResult.RATE_LIMITED, service.changePassword(uuid, "Player",
                "guess123456".toCharArray(), "newPassword12".toCharArray(), IP).join());
        verify(passwords, never()).verifyPasswordAsync(any(), any());
    }

    @Test
    void lockedAccountCannotChangePasswordEvenWithTheRightPassword() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        account.lockUntil(Instant.now().plusSeconds(600));
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.verifyPasswordAsync(any(), eq("hash"))).thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        SecurityService security = auditSink();
        LoginService login = new LoginService(accounts, passwords, permissiveLimits(), mock(SessionService.class),
                security, 5, 900);
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), security, accounts);

        assertEquals(AuthResult.ACCOUNT_LOCKED, service.changePassword(uuid, "Player",
                "rightPassword1".toCharArray(), "newPassword12".toCharArray(), IP).join());
        verify(accounts, never()).updatePasswordHash(any(), any(), any());
    }

    @Test
    void expiredTemporaryPasswordCannotBeChanged() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "temporary-hash");
        account.setTemporaryPassword("temporary-hash", Instant.now().minusSeconds(1));
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.verifyPasswordAsync(any(), eq("temporary-hash")))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        SecurityService security = auditSink();
        LoginService login = new LoginService(accounts, passwords, permissiveLimits(), mock(SessionService.class),
                security, 5, 900);
        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, mock(SessionService.class), security, accounts);

        assertEquals(AuthResult.TEMPORARY_PASSWORD_EXPIRED, service.changePassword(uuid, "Player",
                "Temporary123".toCharArray(), "newPassword12".toCharArray(), IP).join());
        verify(accounts, never()).updatePasswordHash(any(), any(), any());
    }

    private static RateLimitService permissiveLimits() {
        RateLimitService limits = mock(RateLimitService.class);
        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.isAccountRateLimited(anyString())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.recordFailedLoginAttempt(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
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

    @Test
    void revokingSavedSessionsIsAuditedAndAFailedAuditDoesNotFailTheCommand() {
        SessionService sessions = mock(SessionService.class);
        SecurityService security = mock(SecurityService.class);
        AccountRepository accounts = mock(AccountRepository.class);
        UUID uuid = UUID.randomUUID();
        Instant now = Instant.now();
        Account account = new Account(9L, uuid, "Player", "player", "hash",
                io.github.touchfalls.authsecured.domain.account.AccountStatus.ACTIVE, 0, null, now, now, null, now);
        when(sessions.revokeActiveSessionForPlayer(uuid)).thenReturn(CompletableFuture.completedFuture(null));
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(security.logSecurityEvent(any(), any(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        AuthService service = new AuthService(mock(RegistrationService.class), mock(LoginService.class),
                mock(PasswordService.class), sessions, security, accounts);

        service.revokeSavedSessions(uuid).join();
        verify(sessions).revokeActiveSessionForPlayer(uuid);
        verify(security).logSecurityEvent(9L, SecurityEventType.SESSION_REVOKED, null, "{}");

        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("db")));
        service.revokeSavedSessions(uuid).join();
    }
}
