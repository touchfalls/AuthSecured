package com.example.authsecured.ports;

import java.util.concurrent.CompletableFuture;

public interface RateLimitStore {
    CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds);
    CompletableFuture<Boolean> isLocked(String lockKey);
    CompletableFuture<Void> setLock(String lockKey, long lockSeconds);
    CompletableFuture<Void> reset(String key);
}
