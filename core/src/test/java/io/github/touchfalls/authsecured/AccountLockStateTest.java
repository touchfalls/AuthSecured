package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.domain.account.Account;
import io.github.touchfalls.authsecured.domain.account.AccountStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountLockStateTest {
    @Test
    void handlesEveryStatusAndLockExpiryCombination() {
        Instant now = Instant.now();
        for (AccountStatus status : AccountStatus.values()) {
            for (Instant until : new Instant[]{null, now.minusSeconds(1), now.plusSeconds(1)}) {
                Account account = new Account(1L, UUID.randomUUID(), "Player", "player",
                        "hash", status, 0, until, now, now, null, now);
                boolean expected = status == AccountStatus.SUSPENDED
                        || (status == AccountStatus.LOCKED && until == null)
                        || (until != null && now.isBefore(until));
                assertEquals(expected, account.isLocked(now), status + " / " + until);
            }
        }
    }

    @Test
    void nextFailureLocksFollowsTheAtomicIncrementRule() {
        Instant now = Instant.now();
        assertFalse(account(AccountStatus.ACTIVE, 3, null, now).nextFailureLocks(5, now));
        assertTrue(account(AccountStatus.ACTIVE, 4, null, now).nextFailureLocks(5, now));
        // An expired lock restarts the count at 1 instead of re-locking right away.
        assertFalse(account(AccountStatus.LOCKED, 5, now.minusSeconds(1), now).nextFailureLocks(5, now));
        assertTrue(account(AccountStatus.LOCKED, 5, now.minusSeconds(1), now).nextFailureLocks(1, now));
        // A suspension is never turned into a lock.
        assertFalse(account(AccountStatus.SUSPENDED, 4, null, now).nextFailureLocks(5, now));
    }

    private static Account account(AccountStatus status, int failures, Instant until, Instant now) {
        return new Account(1L, UUID.randomUUID(), "Player", "player", "hash", status, failures, until,
                now, now, null, now);
    }
}
