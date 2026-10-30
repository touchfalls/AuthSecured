package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.*;
import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.infrastructure.security.TotpService;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.TotpRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TwoFactorServiceTest {

    /** Just enough of the repository contract for these tests. */
    private static final class MemoryTotpRepository implements TotpRepository {
        byte[] secret;
        boolean enabled;
        long lastStep;
        List<String> backup = new ArrayList<>();

        public CompletableFuture<Optional<State>> find(Long id) {
            return CompletableFuture.completedFuture(secret == null ? Optional.empty()
                    : Optional.of(new State(secret, enabled, lastStep, List.copyOf(backup))));
        }
        public CompletableFuture<Void> savePending(Long id, byte[] encrypted) {
            secret = encrypted; enabled = false; lastStep = 0; backup = new ArrayList<>();
            return CompletableFuture.completedFuture(null);
        }
        public CompletableFuture<Boolean> enable(Long id, long step, List<String> hashes) {
            if (secret == null || enabled) return CompletableFuture.completedFuture(false);
            enabled = true; lastStep = step; backup = new ArrayList<>(hashes);
            return CompletableFuture.completedFuture(true);
        }
        public CompletableFuture<Boolean> useStep(Long id, long step) {
            if (step <= lastStep) return CompletableFuture.completedFuture(false);
            lastStep = step;
            return CompletableFuture.completedFuture(true);
        }
        public CompletableFuture<Boolean> consumeBackupCode(Long id, String hash) {
            return CompletableFuture.completedFuture(backup.remove(hash));
        }
        public CompletableFuture<Void> disable(Long id) {
            secret = null; enabled = false; backup = new ArrayList<>();
            return CompletableFuture.completedFuture(null);
        }
    }

    private final TotpService totp = new TotpService(new byte[32], new byte[32]);
    private final UUID uuid = UUID.randomUUID();
    private final byte[] ip = {1};
    private MemoryTotpRepository repository;
    private AccountRepository accounts;
    private RateLimitService limits;
    private LoginService login;
    private TwoFactorService service;

    @BeforeEach
    void setUp() {
        repository = new MemoryTotpRepository();
        accounts = mock(AccountRepository.class);
        limits = mock(RateLimitService.class);
        login = mock(LoginService.class);
        SecurityService security = mock(SecurityService.class);
        Account account = Account.createNew(uuid, "Player", "player", "hash");
        when(accounts.findByUuid(uuid)).thenReturn(CompletableFuture.completedFuture(Optional.of(account)));
        when(accounts.incrementFailedAttemptsAtomic(any(), anyInt(), anyLong(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        when(limits.isIpRateLimited(any())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.isAccountRateLimited(anyString())).thenReturn(CompletableFuture.completedFuture(false));
        when(limits.recordFailedLoginAttempt(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        when(security.logLoginAttempt(any(), any(), anyBoolean(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(security.logSecurityEvent(any(), any(), any(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(login.completeLogin(any(), eq(uuid), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(false));
        service = new TwoFactorService(accounts, repository, totp, limits, security, login, 5, 900);
    }

    private String currentCode() {
        return TotpService.codeAt(totp.decrypt(repository.secret), TotpService.stepAt(System.currentTimeMillis()));
    }

    private List<String> enable() {
        service.beginSetup(uuid).join();
        return service.confirmSetup(uuid, currentCode(), ip).join().backupCodes();
    }

    @Test
    void setupStaysOffUntilAValidCodeConfirmsIt() {
        TwoFactorService.Outcome started = service.beginSetup(uuid).join();

        assertEquals(TwoFactorService.Status.OK, started.status());
        assertTrue(started.uri().startsWith("otpauth://totp/AuthSecured:Player?secret=" + started.secret()));
        assertFalse(service.isEnabled(uuid).join());

        assertEquals(TwoFactorService.Status.INVALID_CODE, service.confirmSetup(uuid, "000000", ip).join().status());
        assertFalse(service.isEnabled(uuid).join());

        TwoFactorService.Outcome confirmed = service.confirmSetup(uuid, currentCode(), ip).join();
        assertEquals(TwoFactorService.Status.OK, confirmed.status());
        assertEquals(8, confirmed.backupCodes().size());
        assertTrue(service.isEnabled(uuid).join());
        assertEquals(TwoFactorService.Status.ALREADY_ENABLED, service.beginSetup(uuid).join().status());
    }

    @Test
    void aWrongCodeCountsAsAFailedAttemptAndNeverLogsIn() {
        enable();

        assertEquals(TwoFactorService.Status.INVALID_CODE, service.verifyLogin(uuid, "123456", ip).join().status());

        verify(accounts).incrementFailedAttemptsAtomic(any(), eq(5), eq(900L), any());
        verify(limits).recordFailedLoginAttempt("player", ip);
        verify(login, never()).completeLogin(any(), any(), any(), any());
    }

    @Test
    void aValidCodeCompletesTheLoginOnceOnly() {
        enable();
        repository.lastStep = 0; // the code used to confirm must not block the first login
        String code = currentCode();

        assertEquals(TwoFactorService.Status.OK, service.verifyLogin(uuid, code, ip).join().status());
        verify(login).completeLogin(any(), eq(uuid), eq("player"), eq(ip));

        assertEquals(TwoFactorService.Status.INVALID_CODE, service.verifyLogin(uuid, code, ip).join().status(),
                "the same code cannot be replayed");
    }

    @Test
    void aBackupCodeWorksOnceAndDisableNeedsACode() {
        List<String> backup = enable();

        assertEquals(TwoFactorService.Status.OK, service.verifyLogin(uuid, backup.get(0).toLowerCase(), ip).join().status());
        assertEquals(TwoFactorService.Status.INVALID_CODE, service.verifyLogin(uuid, backup.get(0), ip).join().status());

        assertEquals(TwoFactorService.Status.INVALID_CODE, service.disable(uuid, "000000", ip).join().status());
        assertTrue(service.isEnabled(uuid).join());
        assertEquals(TwoFactorService.Status.OK, service.disable(uuid, backup.get(1), ip).join().status());
        assertFalse(service.isEnabled(uuid).join());
        assertEquals(TwoFactorService.Status.NOT_ENABLED, service.verifyLogin(uuid, "123456", ip).join().status());
    }

    @Test
    void limitsAndLocksStopTheCheckBeforeAnyCodeIsTried() {
        enable();
        when(limits.isAccountRateLimited("player")).thenReturn(CompletableFuture.completedFuture(true));

        assertEquals(TwoFactorService.Status.RATE_LIMITED, service.verifyLogin(uuid, currentCode(), ip).join().status());
        verify(login, never()).completeLogin(any(), any(), any(), any());
    }
}
