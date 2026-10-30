package io.github.touchfalls.authsecured;

import io.github.touchfalls.authsecured.application.PasswordService;
import io.github.touchfalls.authsecured.infrastructure.security.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordPolicyTest {

    @Test
    void testPasswordPolicyValidation() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);
        PasswordService passwordService = new PasswordService(hasher, 10, 128, 2);

        assertFalse(passwordService.validatePolicy("short".toCharArray()));
        assertTrue(passwordService.validatePolicy("securePassword123".toCharArray()));
        assertFalse(passwordService.validatePolicy(null));

        passwordService.shutdown();
    }

    @Test
    void testMinimumLengthBoundary() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);
        PasswordService passwordService = new PasswordService(hasher, 8, 128, 2);

        assertEquals(8, passwordService.getMinLength());
        assertTrue(passwordService.validatePolicy("x7Qp9zLm".toCharArray()));
        assertFalse(passwordService.validatePolicy("x7Qp9zL".toCharArray()));

        passwordService.shutdown();
    }

    @Test
    void commonPasswordsAndTheUsernameAreRejected() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);
        PasswordService passwordService = new PasswordService(hasher, 8, 128, 2);

        assertFalse(passwordService.validatePolicy("password123".toCharArray()));
        assertFalse(passwordService.validatePolicy("PassWord123".toCharArray()));
        assertFalse(passwordService.validatePolicy("12345678".toCharArray()));
        assertTrue(passwordService.validatePolicy("correct-horse-battery".toCharArray()));

        assertTrue(PasswordService.matchesUsername("STEVE_123".toCharArray(), "steve_123"));
        assertFalse(PasswordService.matchesUsername("steve_1234".toCharArray(), "steve_123"));
        assertFalse(PasswordService.matchesUsername("steve_123".toCharArray(), null));

        passwordService.shutdown();
    }
}
