package com.example.authsecured.application;

import com.example.authsecured.ports.PasswordHasher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PasswordService {

    private final PasswordHasher passwordHasher;
    private final int minLength;
    private final int maxLength;
    private final ExecutorService authExecutor;

    public PasswordService(PasswordHasher passwordHasher, int minLength, int maxLength, int maxConcurrentJobs) {
        this.passwordHasher = passwordHasher;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.authExecutor = Executors.newFixedThreadPool(Math.max(2, maxConcurrentJobs));
    }

    public boolean validatePolicy(char[] password) {
        if (password == null) return false;
        int len = password.length;
        return len >= minLength && len <= maxLength;
    }

    public CompletableFuture<String> hashPasswordAsync(char[] password) {
        return CompletableFuture.supplyAsync(() -> passwordHasher.hash(password), authExecutor);
    }

    public CompletableFuture<Boolean> verifyPasswordAsync(char[] password, String expectedHash) {
        return CompletableFuture.supplyAsync(() -> passwordHasher.verify(password, expectedHash), authExecutor);
    }

    public CompletableFuture<Boolean> verifyDummyAsync(char[] password) {
        return CompletableFuture.supplyAsync(() -> passwordHasher.verify(password, passwordHasher.getDummyHash()), authExecutor);
    }

    public void shutdown() {
        if (!authExecutor.isShutdown()) {
            authExecutor.shutdown();
        }
    }
}
