package io.github.touchfalls.authsecured.application;

import io.github.touchfalls.authsecured.ports.PasswordHasher;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class PasswordService {

    // Each queued job holds a password in memory until it runs; a flood of login
    // attempts must not grow that queue, and the password memory it holds, without limit.
    private static final int QUEUE_CAPACITY_PER_WORKER = 16;

    private static final char[][] COMMON_PASSWORDS = loadCommonPasswords();

    private final PasswordHasher passwordHasher;
    private final int minLength;
    private final int maxLength;
    private final ExecutorService authExecutor;

    public PasswordService(PasswordHasher passwordHasher, int minLength, int maxLength, int maxConcurrentJobs) {
        this.passwordHasher = passwordHasher;
        this.minLength = minLength;
        this.maxLength = maxLength;
        int workers = Math.max(2, maxConcurrentJobs);
        AtomicInteger threadNumber = new AtomicInteger();
        this.authExecutor = new ThreadPoolExecutor(workers, workers, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(workers * QUEUE_CAPACITY_PER_WORKER), runnable -> {
                    Thread thread = new Thread(runnable, "authsecured-argon2-" + threadNumber.incrementAndGet());
                    // A hash still running when the server stops is no longer needed.
                    thread.setDaemon(true);
                    return thread;
                });
    }

    public boolean validatePolicy(char[] password) {
        if (password == null) return false;
        int len = password.length;
        return len >= minLength && len <= maxLength && !isCommon(password);
    }

    /** True when the password equals the player's name, ignoring case. */
    public static boolean matchesUsername(char[] password, String username) {
        if (password == null || username == null || password.length != username.length()) return false;
        for (int i = 0; i < password.length; i++) {
            if (Character.toLowerCase(password[i]) != Character.toLowerCase(username.charAt(i))) return false;
        }
        return true;
    }

    // Compared as chars so a lower-cased copy of the password never becomes an immutable String.
    private static boolean isCommon(char[] password) {
        char[] lower = new char[password.length];
        try {
            for (int i = 0; i < password.length; i++) lower[i] = Character.toLowerCase(password[i]);
            return Arrays.binarySearch(COMMON_PASSWORDS, lower, Arrays::compare) >= 0;
        } finally {
            Arrays.fill(lower, '\0');
        }
    }

    private static char[][] loadCommonPasswords() {
        try (InputStream in = PasswordService.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) return new char[0][];
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            char[][] list = reader.lines().map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(line -> line.toLowerCase(Locale.ROOT).toCharArray())
                    .toArray(char[][]::new);
            Arrays.sort(list, Arrays::compare);
            return list;
        } catch (IOException e) {
            return new char[0][];
        }
    }

    public int getMinLength() {
        return minLength;
    }

    public boolean needsRehash(String passwordHash) {
        return passwordHasher.needsRehash(passwordHash);
    }

    public CompletableFuture<String> hashPasswordAsync(char[] password) {
        return submit(() -> passwordHasher.hash(password));
    }

    public CompletableFuture<Boolean> verifyPasswordAsync(char[] password, String expectedHash) {
        return submit(() -> passwordHasher.verify(password, expectedHash));
    }

    public CompletableFuture<Boolean> verifyDummyAsync(char[] password) {
        return submit(() -> passwordHasher.verify(password, passwordHasher.getDummyHash()));
    }

    /** Rejections surface as a failed future instead of a RejectedExecutionException
     *  thrown out of this call, matching every caller's existing .exceptionally() handling. */
    private <T> CompletableFuture<T> submit(Supplier<T> task) {
        try {
            return CompletableFuture.supplyAsync(task, authExecutor);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Password hashing queue is full; server is under load", e));
        }
    }

    public void shutdown() {
        if (!authExecutor.isShutdown()) {
            authExecutor.shutdown();
            try {
                // Checks already running finish their database writes before the pool closes.
                if (!authExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    authExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                authExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
