package com.example.authsecured.ports;

/**
 * Abstraction for platform-specific logger engines.
 * Implemented by PaperLogger adapter.
 */
public interface PlatformLogger {
    void info(String message);
    void warning(String message);
    void severe(String message);
    void severe(String message, Throwable throwable);
}
