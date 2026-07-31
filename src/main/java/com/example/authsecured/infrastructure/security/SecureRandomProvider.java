package com.example.authsecured.infrastructure.security;

import java.security.SecureRandom;

public final class SecureRandomProvider {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureRandomProvider() {}

    public static byte[] generateSalt(int length) {
        byte[] salt = new byte[length];
        RANDOM.nextBytes(salt);
        return salt;
    }
}
