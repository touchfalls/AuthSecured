package com.example.authsecured.fabric.adapter;

import com.example.authsecured.ports.PlatformLogger;
import org.slf4j.Logger;

public class FabricLogger implements PlatformLogger {

    private final Logger logger;

    public FabricLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warning(String message) {
        logger.warn(message);
    }

    @Override
    public void severe(String message) {
        logger.error(message);
    }

    @Override
    public void severe(String message, Throwable throwable) {
        logger.error(message, throwable);
    }
}
