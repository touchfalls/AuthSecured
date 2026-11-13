package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.infrastructure.redis.RedisRateLimitStore;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisRateLimitStoreTest {

    @Test
    void requiredRedisNeverFallsBackWhenPoolIsMissing() {
        RedisRateLimitStore required = new RedisRateLimitStore(null, DUMMY_LOGGER,
                Runnable::run, true);
        assertThrows(java.util.concurrent.CompletionException.class,
                () -> required.incrementAndGet("account", 60).join());
    }

    @Test
    void runtimeFailureIsVisibleAsDegradedForOptionalRedis() {
        JedisPool pool = org.mockito.Mockito.mock(JedisPool.class);
        org.mockito.Mockito.when(pool.getResource()).thenThrow(new IllegalStateException("offline"));
        RedisRateLimitStore optional = new RedisRateLimitStore(pool, DUMMY_LOGGER, Runnable::run);
        assertEquals(1, optional.incrementAndGet("account", 60).join());
        assertTrue(optional.isDegraded());
        assertEquals(2, optional.incrementAndGet("account", 60).join());
    }

    @Test
    void redisIsUsedAgainOnceItAnswers() {
        JedisPool pool = org.mockito.Mockito.mock(JedisPool.class);
        Jedis jedis = org.mockito.Mockito.mock(Jedis.class);
        org.mockito.Mockito.when(pool.getResource())
                .thenThrow(new IllegalStateException("offline")).thenReturn(jedis);
        org.mockito.Mockito.when(jedis.eval(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any())).thenReturn(7L);
        // Retry at once; the default waits a minute between attempts.
        RedisRateLimitStore store = new RedisRateLimitStore(pool, DUMMY_LOGGER, Runnable::run, false, 0);

        assertEquals(1, store.incrementAndGet("account", 60).join(), "local count while Redis is down");
        assertEquals(7, store.incrementAndGet("account", 60).join(), "the shared count once it is back");
        assertFalse(store.isDegraded());
    }

    @Test
    @DisplayName("Increment and its expiry run as one atomic Lua script, not two separate calls")
    void incrementUsesOneAtomicScriptInsteadOfIncrThenExpire() {
        JedisPool pool = org.mockito.Mockito.mock(JedisPool.class);
        Jedis jedis = org.mockito.Mockito.mock(Jedis.class);
        org.mockito.Mockito.when(pool.getResource()).thenReturn(jedis);
        org.mockito.Mockito.when(jedis.eval(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.<List<String>>any(),
                org.mockito.ArgumentMatchers.<List<String>>any())).thenReturn(3L);

        RedisRateLimitStore store = new RedisRateLimitStore(pool, DUMMY_LOGGER, Runnable::run);
        assertEquals(3, store.incrementAndGet("account:1", 600).join());

        org.mockito.Mockito.verify(jedis).eval(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.Mockito.eq(List.of("account:1")), org.mockito.Mockito.eq(List.of("600")));
        org.mockito.Mockito.verify(jedis, org.mockito.Mockito.never()).incr(org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(jedis, org.mockito.Mockito.never())
                .expire(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

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
