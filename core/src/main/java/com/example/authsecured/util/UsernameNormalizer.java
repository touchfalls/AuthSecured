package com.example.authsecured.util;

import java.util.Locale;

public final class UsernameNormalizer {

    private UsernameNormalizer() {}

    public static String normalize(String username) {
        if (username == null) {
            return "";
        }
        return username.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        // Minecraft usernames are 3 to 16 characters long and contain only alphanumeric chars and underscores
        return username.matches("^[a-zA-Z0-9_]{3,16}$");
    }
}
