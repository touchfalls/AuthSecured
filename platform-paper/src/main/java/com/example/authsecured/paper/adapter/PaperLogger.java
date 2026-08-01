package com.example.authsecured.paper.adapter;

import com.example.authsecured.ports.PlatformLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaperLogger implements PlatformLogger {

    private final Logger logger;

    public PaperLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warning(message);
    }

    @Override
    public void severe(String message) {
        logger.severe(message);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.log(Level.SEVERE, message, throwable);
    }
}
