package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.auth.AuthResult;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.AccountLimitExceededException;
import io.github.touchfalls.authsecured.ports.DuplicateAccountException;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationServiceSecurityTest {

    @Test
    void logsUnexpectedFailureWithoutSensitiveExceptionText() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        PlatformLogger logger = mock(PlatformLogger.class);
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(accounts.countAccountsByIpHash(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("token=secret")));
        RegistrationService service = new RegistrationService(accounts, passwords, limits,
                mock(SecurityService.class), logger);
        assertEquals(AuthResult.INTERNAL_ERROR, service.register(UUID.randomUUID(), "Player",
                "safePassword12".toCharArray(), new byte[]{1}).join());
        org.mockito.ArgumentCaptor<String> message = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(logger).severe(message.capture());
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().contains("token=secret"));
    }
    @Test
    void databaseDuplicateIsReportedAsAlreadyRegistered() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        SecurityService security = mock(SecurityService.class);
        when(accounts.countAccountsByIpHash(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(limits.isRegistrationAllowed(any(), eq(0L))).thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUsernameNormalized(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(accounts.findByUuid(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.hashPasswordAsync(any())).thenReturn(CompletableFuture.completedFuture("hash"));
        when(accounts.save(any(), any(), anyInt())).thenReturn(CompletableFuture.failedFuture(
                new DuplicateAccountException("duplicate", new IllegalStateException())));

        RegistrationService service = new RegistrationService(accounts, passwords, limits, security);
        assertEquals(AuthResult.ALREADY_REGISTERED,
                service.register(UUID.randomUUID(), "Player", "safePassword12".toCharArray(), new byte[]{1}).join());
    }
    @Test
    void fullIpAtSaveTimeIsReportedAsRateLimited() {
        AccountRepository accounts = mock(AccountRepository.class);
        PasswordService passwords = mock(PasswordService.class);
        RateLimitService limits = mock(RateLimitService.class);
        SecurityService security = mock(SecurityService.class);
        when(accounts.countAccountsByIpHash(any())).thenReturn(CompletableFuture.completedFuture(0L));
        when(limits.isRegistrationAllowed(any(), eq(0L))).thenReturn(CompletableFuture.completedFuture(true));
        when(accounts.findByUsernameNormalized(anyString()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(accounts.findByUuid(any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(passwords.validatePolicy(any())).thenReturn(true);
        when(passwords.hashPasswordAsync(any())).thenReturn(CompletableFuture.completedFuture("hash"));
        when(accounts.save(any(), any(), anyInt())).thenReturn(CompletableFuture.failedFuture(
                new AccountLimitExceededException("full")));

        RegistrationService service = new RegistrationService(accounts, passwords, limits, security);
        assertEquals(AuthResult.RATE_LIMITED,
                service.register(UUID.randomUUID(), "Player", "safePassword12".toCharArray(), new byte[]{1}).join());
    }
}
