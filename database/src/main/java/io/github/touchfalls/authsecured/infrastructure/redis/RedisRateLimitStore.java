package io.github.touchfalls.authsecured.infrastructure.redis;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.RateLimitStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class RedisRateLimitStore implements RateLimitStore {

    // INCR and EXPIRE as two separate calls can leave a key with no expiry if the
    // connection drops in between; that key would then count forever and never
    // reset. One script makes the increment and the TTL atomic.
    private static final String INCREMENT_WITH_EXPIRY_SCRIPT =
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count";

    private final JedisPool jedisPool;
    private final MemoryRateLimitStore fallbackStore;
    private final PlatformLogger logger;
    private final Executor executor;
    private final boolean required;
    private final long retryMillis;
    private final AtomicLong degradedUntilMillis = new AtomicLong();
    private final AtomicBoolean failureLogged = new AtomicBoolean();

    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger) {
        this(jedisPool, logger, java.util.concurrent.ForkJoinPool.commonPool());
    }

    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger, Executor executor) {
        this(jedisPool, logger, executor, false);
    }

    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger, Executor executor,
                               boolean required) {
        this(jedisPool, logger, executor, required, 60_000);
    }

    /** @param retryMillis how long local limits are used after a failure before Redis is tried again */
    public RedisRateLimitStore(JedisPool jedisPool, PlatformLogger logger, Executor executor,
                               boolean required, long retryMillis) {
        this.retryMillis = retryMillis;
        this.jedisPool = jedisPool;
        this.fallbackStore = new MemoryRateLimitStore();
        this.logger = logger;
        this.executor = executor;
        this.required = required;
    }

    public boolean isDegraded() { return System.currentTimeMillis() < degradedUntilMillis.get(); }

    @Override
    public CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        if (jedisPool == null || isDegraded()) {
            return required ? unavailable() : fallbackStore.incrementAndGet(key, windowSeconds);
        }
        return withFallback(CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                Object result = jedis.eval(INCREMENT_WITH_EXPIRY_SCRIPT,
                        List.of(key), List.of(Long.toString(windowSeconds)));
                return ((Number) result).intValue();
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.incrementAndGet(key, windowSeconds));
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        if (jedisPool == null || isDegraded()) {
            return required ? unavailable() : fallbackStore.isLocked(lockKey);
        }
        return withFallback(CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(lockKey);
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.isLocked(lockKey));
    }

    @Override
    public CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        if (jedisPool == null || isDegraded()) {
            return required ? unavailable() : fallbackStore.setLock(lockKey, lockSeconds);
        }
        return withFallback(CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(lockKey, lockSeconds, "LOCKED");
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.setLock(lockKey, lockSeconds));
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        if (jedisPool == null || isDegraded()) {
            return required ? unavailable() : fallbackStore.reset(key);
        }
        return withFallback(CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                throw new IllegalStateException("Redis operation failed", e);
            }
        }, executor), () -> fallbackStore.reset(key));
    }

    private <T> CompletableFuture<T> withFallback(CompletableFuture<T> operation,
                                                   Supplier<CompletableFuture<T>> fallback) {
        return operation.orTimeout(5, TimeUnit.SECONDS).whenComplete((result, error) -> {
            if (error == null && failureLogged.compareAndSet(true, false)) {
                logger.info("Redis is reachable again; shared rate limits are back in use.");
            }
        }).exceptionallyCompose(error -> {
            if (failureLogged.compareAndSet(false, true)) {
                logger.severe(required
                        ? "Required Redis failed; authentication operations will be rejected. Connection details are hidden."
                        : "Redis failed; rate limits are local to this server until it answers again "
                        + "(retried every minute). Connection details are hidden.");
            }
            if (required) return unavailable();
            degradedUntilMillis.set(System.currentTimeMillis() + retryMillis);
            return fallback.get();
        });
    }

    private static <T> CompletableFuture<T> unavailable() {
        return CompletableFuture.failedFuture(new IllegalStateException("Required Redis is unavailable"));
    }
}
