package com.example.authsecured.infrastructure.redis;

import com.example.authsecured.ports.RateLimitStore;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

public class MemoryRateLimitStore implements RateLimitStore {

    private static class Counter {
        int count;
        long expireAtEpochSecond;

        Counter(int count, long expireAtEpochSecond) {
            this.count = count;
            this.expireAtEpochSecond = expireAtEpochSecond;
        }
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final Map<String, Long> locks = new ConcurrentHashMap<>();

    @Override
    public CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        return CompletableFuture.supplyAsync(() -> {
            long now = Instant.now().getEpochSecond();
            cleanExpired(now);
            Counter updated = counters.compute(key, (k, existing) -> {
                if (existing == null || existing.expireAtEpochSecond < now) {
                    return new Counter(1, now + windowSeconds);
                } else {
                    existing.count++;
                    return existing;
                }
            });
            return updated.count;
        });
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        return CompletableFuture.supplyAsync(() -> {
            long now = Instant.now().getEpochSecond();
            Long expire = locks.get(lockKey);
            if (expire == null) return false;
            if (expire < now) {
                locks.remove(lockKey, expire);
                return false;
            }
            return true;
        });
    }

    @Override
    public CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        return CompletableFuture.runAsync(() -> {
            long expireAt = Instant.now().getEpochSecond() + lockSeconds;
            locks.put(lockKey, expireAt);
        });
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        return CompletableFuture.runAsync(() -> {
            counters.remove(key);
            locks.remove(key);
        });
    }

    private void cleanExpired(long now) {
        if (counters.size() > 100) {
            counters.entrySet().removeIf(entry -> entry.getValue().expireAtEpochSecond < now);
        }
        if (locks.size() > 100) {
            locks.entrySet().removeIf(entry -> entry.getValue() < now);
        }
    }
}
