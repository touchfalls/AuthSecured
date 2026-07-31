package com.example.authsecured.util;

import java.util.regex.Pattern;

public final class SecretRedactor {

    private static final Pattern SENSITIVE_PATTERN = Pattern.compile(
            "(?i)(password|pass|secret|token|hash|authkey)=['\"]?[^'\"\\s]+['\"]?"
    );

    private SecretRedactor() {}

    public static String redact(String input) {
        if (input == null) return null;
        return SENSITIVE_PATTERN.matcher(input).replaceAll("$1=[REDACTED]");
    }
}
