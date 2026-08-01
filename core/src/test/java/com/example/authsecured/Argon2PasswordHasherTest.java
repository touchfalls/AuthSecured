package com.example.authsecured;

import com.example.authsecured.infrastructure.security.Argon2PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Argon2PasswordHasherTest {

    @Test
    void testHashAndVerify() {
        // Low memory and iteration settings for fast test execution
        Argon2PasswordHasher hasher = new Argon2PasswordHasher(1024, 1, 1, 16, 8);

        char[] password = "MySecretPassword123!".toCharArray();
        String hash = hasher.hash(password);

        assertNotNull(hash);
        assertTrue(hash.startsWith("$argon2id$"));

        assertTrue(hasher.verify(password, hash));
        assertFalse(hasher.verify("WrongPassword".toCharArray(), hash));
    }
}
