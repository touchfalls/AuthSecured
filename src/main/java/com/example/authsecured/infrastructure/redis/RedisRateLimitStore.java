package com.example.authsecured.infrastructure.redis;

import com.example.authsecured.ports.RateLimitStore;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

public class RedisRateLimitStore implements RateLimitStore {

    private final JedisPool jedisPool;
    private final MemoryRateLimitStore fallbackStore;
    private final Logger logger;

    public RedisRateLimitStore(JedisPool jedisPool, Logger logger) {
        this.jedisPool = jedisPool;
        this.fallbackStore = new MemoryRateLimitStore();
        this.logger = logger;
    }

    @Override
    public CompletableFuture<Integer> incrementAndGet(String key, long windowSeconds) {
        if (jedisPool == null) {
            return fallbackStore.incrementAndGet(key, windowSeconds);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                long count = jedis.incr(key);
                if (count == 1) {
                    jedis.expire(key, windowSeconds);
                }
                return (int) count;
            } catch (Exception e) {
                logger.warning("Redis operation failed, falling back to memory: " + e.getMessage());
                return fallbackStore.incrementAndGet(key, windowSeconds).join();
            }
        });
    }

    @Override
    public CompletableFuture<Boolean> isLocked(String lockKey) {
        if (jedisPool == null) {
            return fallbackStore.isLocked(lockKey);
        }
        return CompletableFuture.supplyAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                return jedis.exists(lockKey);
            } catch (Exception e) {
                logger.warning("Redis operation failed, falling back to memory: " + e.getMessage());
                return fallbackStore.isLocked(lockKey).join();
            }
        });
    }

    @Override
    public CompletableFuture<Void> setLock(String lockKey, long lockSeconds) {
        if (jedisPool == null) {
            return fallbackStore.setLock(lockKey, lockSeconds);
        }
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.setex(lockKey, lockSeconds, "LOCKED");
            } catch (Exception e) {
                logger.warning("Redis operation failed, falling back to memory: " + e.getMessage());
                fallbackStore.setLock(lockKey, lockSeconds).join();
            }
        });
    }

    @Override
    public CompletableFuture<Void> reset(String key) {
        if (jedisPool == null) {
            return fallbackStore.reset(key);
        }
        return CompletableFuture.runAsync(() -> {
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.del(key);
            } catch (Exception e) {
                logger.warning("Redis operation failed, falling back to memory: " + e.getMessage());
                fallbackStore.reset(key).join();
            }
        });
    }
}
