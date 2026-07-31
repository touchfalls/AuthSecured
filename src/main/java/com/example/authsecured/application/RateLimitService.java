package com.example.authsecured.application;

import com.example.authsecured.ports.RateLimitStore;

import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;

public class RateLimitService {

    private final RateLimitStore rateLimitStore;
    private final int maxAccountAttempts;
    private final long accountWindowSeconds;
    private final long accountLockSeconds;
    private final int maxIpAttempts;
    private final long ipWindowSeconds;
    private final long ipLockSeconds;
    private final int maxRegPerIp;
    private final int maxRegPerHour;

    public RateLimitService(RateLimitStore rateLimitStore, int maxAccountAttempts, long accountWindowSeconds,
                            long accountLockSeconds, int maxIpAttempts, long ipWindowSeconds,
                            long ipLockSeconds, int maxRegPerIp, int maxRegPerHour) {
        this.rateLimitStore = rateLimitStore;
        this.maxAccountAttempts = maxAccountAttempts;
        this.accountWindowSeconds = accountWindowSeconds;
        this.accountLockSeconds = accountLockSeconds;
        this.maxIpAttempts = maxIpAttempts;
        this.ipWindowSeconds = ipWindowSeconds;
        this.ipLockSeconds = ipLockSeconds;
        this.maxRegPerIp = maxRegPerIp;
        this.maxRegPerHour = maxRegPerHour;
    }

    public CompletableFuture<Boolean> isAccountRateLimited(String usernameNormalized) {
        if (usernameNormalized == null || usernameNormalized.isBlank()) {
            return CompletableFuture.completedFuture(false);
        }
        String lockKey = "authsecured:lock:account:" + usernameNormalized;
        return rateLimitStore.isLocked(lockKey);
    }

    public CompletableFuture<Boolean> isIpRateLimited(byte[] ipHash) {
        if (ipHash == null || ipHash.length == 0) {
            return CompletableFuture.completedFuture(false);
        }
        String hexIp = HexFormat.of().formatHex(ipHash);
        String lockKey = "authsecured:lock:ip:" + hexIp;
        return rateLimitStore.isLocked(lockKey);
    }

    public CompletableFuture<Void> recordFailedLoginAttempt(String usernameNormalized, byte[] ipHash) {
        CompletableFuture<Integer> accountAttempts = (usernameNormalized != null && !usernameNormalized.isBlank())
                ? rateLimitStore.incrementAndGet("authsecured:ratelimit:account:" + usernameNormalized, accountWindowSeconds)
                : CompletableFuture.completedFuture(0);

        String hexIp = (ipHash != null && ipHash.length > 0) ? HexFormat.of().formatHex(ipHash) : null;
        CompletableFuture<Integer> ipAttempts = (hexIp != null)
                ? rateLimitStore.incrementAndGet("authsecured:ratelimit:ip:" + hexIp, ipWindowSeconds)
                : CompletableFuture.completedFuture(0);

        return accountAttempts.thenCombine(ipAttempts, (acc, ip) -> {
            CompletableFuture<Void> f1 = (acc >= maxAccountAttempts && usernameNormalized != null)
                    ? rateLimitStore.setLock("authsecured:lock:account:" + usernameNormalized, accountLockSeconds)
                    : CompletableFuture.completedFuture(null);

            CompletableFuture<Void> f2 = (ip >= maxIpAttempts && hexIp != null)
                    ? rateLimitStore.setLock("authsecured:lock:ip:" + hexIp, ipLockSeconds)
                    : CompletableFuture.completedFuture(null);

            return CompletableFuture.allOf(f1, f2);
        }).thenCompose(future -> future);
    }

    public CompletableFuture<Void> recordSuccessfulLogin(String usernameNormalized) {
        if (usernameNormalized == null || usernameNormalized.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        String accountKey = "authsecured:ratelimit:account:" + usernameNormalized;
        String lockKey = "authsecured:lock:account:" + usernameNormalized;
        return rateLimitStore.reset(accountKey).thenCompose(v -> rateLimitStore.reset(lockKey));
    }

    public CompletableFuture<Boolean> isRegistrationAllowed(byte[] ipHash, long registeredAccountCount) {
        if (registeredAccountCount >= maxRegPerIp) {
            return CompletableFuture.completedFuture(false);
        }
        if (ipHash == null || ipHash.length == 0) {
            return CompletableFuture.completedFuture(true);
        }
        String hexIp = HexFormat.of().formatHex(ipHash);
        String regKey = "authsecured:ratelimit:reg:" + hexIp;
        return rateLimitStore.incrementAndGet(regKey, 3600L)
                .thenApply(attempts -> attempts <= maxRegPerHour);
    }
}
