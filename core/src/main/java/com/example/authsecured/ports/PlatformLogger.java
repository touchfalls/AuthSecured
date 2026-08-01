package com.example.authsecured.ports;

/**
 * Abstraction layer for platform-agnostic logging.
 * Implemented by PaperLogger and FabricLogger adapters.
 */
public interface PlatformLogger {
    void info(String message);
    void warning(String message);
    void severe(String message);
    void severe(String message, Throwable throwable);
}
