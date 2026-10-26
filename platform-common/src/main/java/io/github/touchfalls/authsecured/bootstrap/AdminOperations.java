package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.domain.auth.TemporaryPasswordReset;
import io.github.touchfalls.authsecured.domain.security.AuditEntry;
import io.github.touchfalls.authsecured.util.UsernameNormalizer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Shared implementation behind the Paper and Fabric administrative command adapters. */
public final class AdminOperations {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DependencyContainer container;

    public AdminOperations(DependencyContainer container) {
        this.container = container;
    }

    public long getSessionTimeoutSeconds() {
        return container.getSessionService().getTimeoutSeconds();
    }

    public void setSessionTimeoutSeconds(long seconds) {
        container.getSessionService().setTimeoutSeconds(seconds);
    }

    public CompletableFuture<List<String>> status() {
        return container.getDatabaseManager().ping().thenApply(databaseUp -> {
            String disabled = message("messages.admin-state-disabled", "disabled");
            String databaseState = databaseUp
                    ? message("messages.admin-state-online", "online")
                    : message("messages.admin-state-unavailable", "unavailable");
            String backend = container.getRateLimitBackend();
            String localizedBackend = message("messages.admin-backend-" + backend.toLowerCase(Locale.ROOT), backend);
            String sessionSeconds = seconds(container.getSessionService().getTimeoutSeconds());
            String sessionsState = container.getSessionService().isEnabled()
                    ? message("messages.admin-state-sessions-enabled", "enabled")
                    : message("messages.admin-state-sessions-disabled", "disabled");
            String auditState = container.getSecurityService().isAuditEnabled()
                    ? message("messages.admin-state-audit-enabled", "enabled")
                    : message("messages.admin-state-audit-disabled", "disabled");
            String authTimeout = container.getAuthenticationTimeoutService().isEnabled()
                    ? seconds(container.getAuthenticationTimeoutService().getTimeoutSeconds()) : disabled;
            return List.of(
                    message("messages.admin-status-title", "&eAuthSecured status"),
                    message("messages.admin-status-database", "&7Database: {color}{type} {state}", Map.of(
                            "color", color(databaseUp),
                            "type", container.getDatabaseManager().getDatabaseType(),
                            "state", databaseState)),
                    message("messages.admin-status-database-pool",
                            "&7Database pool: &f{active} active, {idle} idle", Map.of(
                                    "active", Integer.toString(container.getDatabaseManager().getActiveConnections()),
                                    "idle", Integer.toString(container.getDatabaseManager().getIdleConnections()))),
                    message("messages.admin-status-rate-limit", "&7Rate limiting: &f{backend}",
                            Map.of("backend", localizedBackend)),
                    message("messages.admin-status-sessions", "&7Sessions: &f{state}, {timeout}", Map.of(
                            "state", sessionsState,
                            "timeout", sessionSeconds)),
                    message("messages.admin-status-auth-timeout", "&7Authentication timeout: &f{value}",
                            Map.of("value", authTimeout)),
                    message("messages.admin-status-audit", "&7Audit: &f{state}", Map.of(
                            "state", auditState))
            );
        });
    }

    public CompletableFuture<List<String>> doctor() {
        return container.getDatabaseManager().ping().thenApply(databaseUp -> {
            List<String> lines = new ArrayList<>();
            lines.add(message("messages.admin-doctor-title", "&eAuthSecured doctor"));
            addCheck(lines, databaseUp,
                    message("messages.admin-doctor-database-ok", "Database connection"),
                    message("messages.admin-doctor-database-fail", "Database is unavailable"));
            addCheck(lines, !"memory-fallback".equals(container.getRateLimitBackend()),
                    message("messages.admin-doctor-redis-ok", "Rate-limit backend"),
                    message("messages.admin-doctor-redis-fail", "Redis is unavailable; limits are local only"));
            // A file-generated secret is unique per server; a network sharing one database or
            // Redis instance needs the same AUTHSECURED_IP_SECRET on every server, or IP-bound
            // sessions, IP rate limits and the per-IP registration cap silently stop matching
            // across servers.
            boolean sharedBackend = container.getConfigManager().getBoolean("redis.enabled", false)
                    || "postgresql".equals(container.getDatabaseManager().getDatabaseType());
            addCheck(lines, container.isIpHashSecretFromEnvironment() || !sharedBackend,
                    message("messages.admin-doctor-ip-secret-ok", "IP hashing secret"),
                    message("messages.admin-doctor-ip-secret-fail",
                            "IP hashing secret comes from a per-server file, but a shared database or "
                                    + "Redis is enabled. Set the same AUTHSECURED_IP_SECRET on every server."));
            // Without IP binding a session is keyed only by UUID, which an offline-mode server
            // derives from the player name: anyone joining under that name inherits it.
            addCheck(lines, !container.getSessionService().isEnabled()
                            || container.getSessionService().isVerifyIp()
                            || container.getPlatform().isOnlineMode(),
                    message("messages.admin-doctor-session-ip-ok", "Session IP binding"),
                    message("messages.admin-doctor-session-ip-fail",
                            "Sessions are not bound to the player's IP on an offline-mode server, so anyone "
                                    + "joining with the same name inherits them. Set session.verify-ip: true."));
            addCheck(lines, meetsOwaspArgon2Minimum(
                            container.getConfigManager().getInt("security.password.argon2.memory-kib", 65536),
                            container.getConfigManager().getInt("security.password.argon2.iterations", 3)),
                    message("messages.admin-doctor-argon2-ok", "Password hashing cost"),
                    message("messages.admin-doctor-argon2-fail",
                            "Argon2 settings are below the OWASP minimum (for example 19456 KiB with 2 "
                                    + "iterations). Raise security.password.argon2 memory-kib or iterations."));
            if ("postgresql".equals(container.getDatabaseManager().getDatabaseType())) {
                String sslMode = postgresSslMode();
                addCheck(lines, isLoopbackHost(container.getConfigManager().getString(
                                "database.postgresql.host", "localhost"))
                                || !Set.of("disable", "allow").contains(sslMode),
                        message("messages.admin-doctor-database-tls-ok", "Database transport encryption"),
                        message("messages.admin-doctor-database-tls-fail",
                                "PostgreSQL on a remote host uses ssl-mode {mode}, which can send data "
                                        + "unencrypted. Use verify-full.", Map.of("mode", sslMode)));
            }
            boolean notificationEnabled = container.getConfigManager().getBoolean(
                    "notifications.login-failures.enabled", false);
            boolean discordTarget = container.getConfigManager().getBoolean(
                    "notifications.discord.enabled", true)
                    && hasAnyValue("notifications.discord.webhook-url",
                    "notifications.discord.webhook-url-env");
            boolean telegramTarget = container.getConfigManager().getBoolean(
                    "notifications.telegram.enabled", true)
                    && hasAnyValue("notifications.telegram.bot-token", "notifications.telegram.bot-token-env")
                    && hasAnyValue("notifications.telegram.chat-id", "notifications.telegram.chat-id-env");
            boolean notificationTarget = discordTarget || telegramTarget;
            addCheck(lines, !notificationEnabled || notificationTarget,
                    message("messages.admin-doctor-alerts-ok", "Login alerts"),
                    message("messages.admin-doctor-alerts-fail",
                            "Alerts are enabled but neither Discord nor Telegram is configured"));
            addCheck(lines, container.getSecurityService().isAuditEnabled(),
                    message("messages.admin-doctor-audit-ok", "Audit logging"),
                    message("messages.admin-doctor-audit-fail", "Audit logging is disabled"));
            lines.add(lines.stream().anyMatch(line -> line.startsWith("§c"))
                    ? message("messages.admin-doctor-problems", "&cDoctor found configuration problems.")
                    : message("messages.admin-doctor-healthy", "&aDoctor found no critical problems."));
            return List.copyOf(lines);
        });
    }

    public CompletableFuture<List<String>> audit(int limit, String username) {
        String normalized = username == null || username.isBlank()
                ? "" : UsernameNormalizer.normalize(username);
        return container.getSecurityService().findRecentAuditEntries(limit, normalized)
                .thenApply(entries -> formatAudit(entries, normalized));
    }

    public CompletableFuture<TemporaryPasswordReset> issueTemporaryPassword(String username) {
        int length = container.getConfigManager().getInt(
                "admin.temporary-password.length", 18, 12, 64);
        long expiry = container.getConfigManager().getInt(
                "admin.temporary-password.expires-seconds", 900, 60, 86_400);
        return container.getAuthService().issueTemporaryPassword(username, length, expiry)
                .thenApply(reset -> {
                    // The old password no longer works, so neither may a connection that used it.
                    if (reset != null) {
                        container.getCommandExecutor().revokeAuthentication(reset.getPlayerUuid(),
                                "messages.admin-password-reset-notice",
                                "&cAn administrator reset your password. Log in with the temporary password you were given.");
                    }
                    return reset;
                });
    }

    public CompletableFuture<Boolean> unregister(String username) {
        return container.getAuthService().unregisterAccount(username)
                .thenApply(deleted -> {
                    deleted.ifPresent(playerUuid -> container.getCommandExecutor().revokeAuthentication(playerUuid,
                            "messages.admin-unregistered-notice",
                            "&cAn administrator deleted your account. Register again to continue."));
                    return deleted.isPresent();
                });
    }

    private List<String> formatAudit(List<AuditEntry> entries, String normalizedUsername) {
        List<String> lines = new ArrayList<>();
        lines.add(normalizedUsername.isBlank()
                ? message("messages.admin-audit-title", "&eRecent AuthSecured audit entries")
                : message("messages.admin-audit-title-player", "&eRecent audit entries for {player}",
                Map.of("player", normalizedUsername)));
        if (entries.isEmpty()) {
            lines.add(message("messages.admin-audit-empty", "&7No matching entries."));
            return List.copyOf(lines);
        }
        for (AuditEntry entry : entries) {
            String username = entry.username() == null
                    ? message("messages.admin-audit-unknown-player", "unknown") : entry.username();
            String category = auditToken("category", entry.category());
            String detail = auditToken("event", entry.reason() != null ? entry.reason() : entry.event());
            String outcome = entry.successful() == null ? "" : " " + (entry.successful()
                    ? message("messages.admin-audit-success", "success")
                    : message("messages.admin-audit-failure", "failure"));
            lines.add(message("messages.admin-audit-line",
                    "&7{time} &f{player} &8[{category}{outcome}] &7{detail}", Map.of(
                            "time", TIME_FORMAT.format(entry.createdAt()),
                            "player", username,
                            "category", category,
                            "outcome", outcome,
                            "detail", detail)));
        }
        return List.copyOf(lines);
    }

    private void addCheck(List<String> lines, boolean healthy, String success, String failure) {
        lines.add(message(healthy ? "messages.admin-doctor-check-ok" : "messages.admin-doctor-check-fail",
                healthy ? "&a[OK] &7{message}" : "&c[FAIL] &7{message}",
                Map.of("message", healthy ? success : failure)));
    }

    private String auditToken(String group, String value) {
        if (value == null || value.isBlank()) {
            return message("messages.admin-audit-unknown-event", "unknown");
        }
        String key = value.toLowerCase(Locale.ROOT).replace('_', '-');
        String fallback = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return message("messages.admin-audit-" + group + "-" + key, fallback);
    }

    private String seconds(long value) {
        return message("messages.admin-seconds", "{seconds} seconds",
                Map.of("seconds", Long.toString(value)));
    }

    private String message(String key, String fallback) {
        return container.getConfigManager().getMessage(key, fallback);
    }

    private String message(String key, String fallback, Map<String, String> placeholders) {
        return container.getConfigManager().getMessage(key, fallback, placeholders);
    }

    /**
     * OWASP's minimum Argon2id configurations trade memory for iterations: 46 MiB with one
     * pass, 19 MiB with two, 12 MiB with three, 9 MiB with four or 7 MiB with five.
     */
    static boolean meetsOwaspArgon2Minimum(int memoryKib, int iterations) {
        return (memoryKib >= 47_104 && iterations >= 1)
                || (memoryKib >= 19_456 && iterations >= 2)
                || (memoryKib >= 12_288 && iterations >= 3)
                || (memoryKib >= 9_216 && iterations >= 4)
                || (memoryKib >= 7_168 && iterations >= 5);
    }

    private String postgresSslMode() {
        String fallback = container.getConfigManager().getBoolean("database.postgresql.ssl", false)
                ? "require" : "prefer";
        return container.getConfigManager().getString("database.postgresql.ssl-mode", fallback)
                .toLowerCase(Locale.ROOT);
    }

    static boolean isLoopbackHost(String host) {
        if (host == null) return true;
        String normalized = host.strip().toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("::1") || normalized.equals("[::1]")
                || normalized.matches("127(\\.\\d{1,3}){3}");
    }

    private static String color(boolean healthy) {
        return healthy ? "§a" : "§c";
    }

    private boolean hasAnyValue(String directPath, String environmentPath) {
        return !container.getConfigManager().getString(directPath, "").isBlank()
                || !container.getConfigManager().getString(environmentPath, "").isBlank();
    }
}
