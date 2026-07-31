package com.example.authsecured.util;

import java.time.Instant;

public interface TimeProvider {
    Instant now();

    class SystemTimeProvider implements TimeProvider {
        @Override
        public Instant now() {
            return Instant.now();
        }
    }
}
