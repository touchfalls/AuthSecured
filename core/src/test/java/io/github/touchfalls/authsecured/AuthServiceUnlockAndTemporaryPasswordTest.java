package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.domain.security.SecurityEventType;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceUnlockAndTemporaryPasswordTest {

    @Test
    void unlockClearsTheRateLimitLockSoTheAdminActionTakesEffectImmediately() {
        AccountRepository accounts = mock(AccountRepository.class);
        LoginService login = mock(LoginService.class);
        SecurityService security = mock(SecurityService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(accounts.findByUsernameNormalized("player")).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.unlock(eq(account.getId()), any())).thenReturn(CompletableFuture.completedFuture(true));
        when(login.clearRateLimit("player")).thenReturn(CompletableFuture.completedFuture(null));
        when(security.logSecurityEvent(any(), eq(SecurityEventType.ACCOUNT_UNLOCKED), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AuthService service = new AuthService(mock(RegistrationService.class), login,
                mock(PasswordService.class), mock(SessionService.class), security, accounts);

        assertTrue(service.unlockAccount("Player", null).join());
        // Without this, a player an admin just unlocked could still be told "too many attempts".
        verify(login).clearRateLimit("player");
    }

    @Test
    void issuingATemporaryPasswordClearsTheRateLimitLockToo() {
        AccountRepository accounts = mock(AccountRepository.class);
        LoginService login = mock(LoginService.class);
        PasswordService passwords = mock(PasswordService.class);
        SessionService sessions = mock(SessionService.class);
        SecurityService security = mock(SecurityService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(accounts.findByUsernameNormalized("player")).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(passwords.hashPasswordAsync(any())).thenReturn(CompletableFuture.completedFuture("temp-hash"));
        when(accounts.setTemporaryPassword(eq(account.getId()), eq("temp-hash"), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(sessions.revokeAllSessionsForAccount(account.getId())).thenReturn(CompletableFuture.completedFuture(null));
        when(login.clearRateLimit("player")).thenReturn(CompletableFuture.completedFuture(null));
        when(security.logSecurityEvent(any(), eq(SecurityEventType.TEMPORARY_PASSWORD_ISSUED), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        AuthService service = new AuthService(mock(RegistrationService.class), login,
                passwords, sessions, security, accounts);

        TemporaryPasswordReset reset = service.issueTemporaryPassword("Player", 18, 900, null).join();
        assertEquals(18, reset.copyPassword().length);
        // Lets the caller end the in-game authentication of the account owner.
        assertEquals(uuid, reset.getPlayerUuid());
        verify(login).clearRateLimit("player");
    }

    @Test
    void unregisterAccountReportsTheOwnerOfTheDeletedAccount() {
        AccountRepository accounts = mock(AccountRepository.class);
        SessionService sessions = mock(SessionService.class);
        UUID uuid = UUID.randomUUID();
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(accounts.findByUsernameNormalized("player")).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.findByUsernameNormalized("missing")).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(accounts.delete(uuid)).thenReturn(CompletableFuture.completedFuture(true));
        when(sessions.revokeAllSessionsForAccount(account.getId())).thenReturn(CompletableFuture.completedFuture(null));

        SecurityService security = mock(SecurityService.class);
        when(security.logSecurityEvent(any(), any(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        UUID admin = UUID.randomUUID();

        AuthService service = new AuthService(mock(RegistrationService.class), mock(LoginService.class),
                mock(PasswordService.class), sessions, security, accounts);

        assertEquals(Optional.of(uuid), service.unregisterAccount("Player", admin).join());
        assertEquals(Optional.empty(), service.unregisterAccount("missing", admin).join());
        verify(sessions).revokeAllSessionsForAccount(account.getId());
        // Deleting an account used to leave no trace of who did it, or that it happened at all.
        verify(security).logSecurityEvent(null, SecurityEventType.ADMIN_ACTION, null,
                "{\"actor\":\"" + admin + "\",\"unregistered\":\"player\"}");
    }
}
