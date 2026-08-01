package com.example.authsecured;

import com.example.authsecured.util.UsernameNormalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsernameNormalizerTest {

    @Test
    void testNormalize() {
        assertEquals("player", UsernameNormalizer.normalize("Player"));
        assertEquals("player", UsernameNormalizer.normalize("  PLAYER  "));
        assertEquals("", UsernameNormalizer.normalize(null));
    }

    @Test
    void testIsValidUsername() {
        assertTrue(UsernameNormalizer.isValidUsername("Player123"));
        assertTrue(UsernameNormalizer.isValidUsername("Valid_Name"));
        assertFalse(UsernameNormalizer.isValidUsername("ab")); // too short
        assertFalse(UsernameNormalizer.isValidUsername("ThisUsernameIsWayTooLong")); // > 16 chars
        assertFalse(UsernameNormalizer.isValidUsername("Invalid Name!")); // special chars
    }
}
