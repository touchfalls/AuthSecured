package com.example.authsecured;

import com.example.authsecured.application.RateLimitService;
import com.example.authsecured.infrastructure.redis.MemoryRateLimitStore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitServiceTest {

    @Test
    void testRateLimitLocking() {
        MemoryRateLimitStore store = new MemoryRateLimitStore();
        RateLimitService service = new RateLimitService(store, 3, 60, 300, 10, 60, 300, 3, 10);

        byte[] ipHash = new byte[]{1, 2, 3};
        String user = "testuser";

        assertFalse(service.isAccountRateLimited(user).join());

        service.recordFailedLoginAttempt(user, ipHash).join();
        service.recordFailedLoginAttempt(user, ipHash).join();
        assertFalse(service.isAccountRateLimited(user).join());

        service.recordFailedLoginAttempt(user, ipHash).join();
        assertTrue(service.isAccountRateLimited(user).join());

        service.recordSuccessfulLogin(user).join();
        assertFalse(service.isAccountRateLimited(user).join());
    }
}
