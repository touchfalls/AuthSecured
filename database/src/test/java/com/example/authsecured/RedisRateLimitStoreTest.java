package com.example.authsecured;

import com.example.authsecured.infrastructure.redis.RedisRateLimitStore;
import com.example.authsecured.ports.PlatformLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateLimitStoreTest {

    private RedisRateLimitStore rateLimitStore;
    private static final PlatformLogger DUMMY_LOGGER = new PlatformLogger() {
        @Override public void info(String message) {}
        @Override public void warning(String message) {}
        @Override public void severe(String message) {}
        @Override public void severe(String message, Throwable throwable) {}
    };

    @BeforeEach
    void setUp() {
        // Initialize with null JedisPool to test memory fallback safety
        rateLimitStore = new RedisRateLimitStore(null, DUMMY_LOGGER);
    }

    @Test
    @DisplayName("Verify rate limit store falls back gracefully to in-memory store when Redis is unavailable")
    void testMemoryFallbackWhenRedisNull() {
        String key = "test:account:1001";

        int attempts = rateLimitStore.incrementAndGet(key, 60).join();
        assertEquals(1, attempts);

        attempts = rateLimitStore.incrementAndGet(key, 60).join();
        assertEquals(2, attempts);

        assertFalse(rateLimitStore.isLocked("lock:key").join());
        rateLimitStore.setLock("lock:key", 300).join();
        assertTrue(rateLimitStore.isLocked("lock:key").join());

        rateLimitStore.reset(key).join();
        assertEquals(1, rateLimitStore.incrementAndGet(key, 60).join());
    }
}
