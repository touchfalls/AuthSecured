package io.github.touchfalls.authsecured.infrastructure.config;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {

    private final File dataFolder;
    private final PlatformLogger logger;
    private final LocalizationManager localizationManager;
    private volatile Map<String, Object> configValues = Map.of();

    public ConfigManager(File dataFolder, PlatformLogger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.localizationManager = new LocalizationManager(dataFolder, "en", logger);
    }

    public boolean loadConfigurations() {
        File configFile = new File(dataFolder, "config.yml");
        try {
            Files.createDirectories(dataFolder.toPath());
            saveDefaultIfNotExists(configFile, "config.yml");

            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> loadedValues;
            boolean notificationSectionPresent = false;
            try (InputStream input = new FileInputStream(configFile)) {
                Object loaded = yaml.load(input);
                if (loaded == null) {
                    loadedValues = new LinkedHashMap<>();
                } else if (loaded instanceof Map<?, ?> root) {
                    notificationSectionPresent = root.containsKey("notifications");
                    loadedValues = flatten(root);
                } else {
                    throw new IllegalArgumentException("the YAML root must be a mapping");
                }
            }

            validate(loadedValues);
            migrateNotificationSettings(configFile, loadedValues, notificationSectionPresent);

            // Publish a complete, immutable snapshot so event threads never see a
            // partially reloaded configuration.
            configValues = Collections.unmodifiableMap(new LinkedHashMap<>(loadedValues));
            localizationManager.init(valueAsString(loadedValues, "language", "en"));
            return true;
        } catch (Exception e) {
            logger.severe("Failed to load config.yml; keeping the last valid configuration. "
                    + "File contents and exception details are hidden.");
            return false;
        }
    }

    private void validate(Map<String, Object> values) {
        String language = valueAsString(values, "language", "en").toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("en", "ru", "es", "it", "fr").contains(language)) {
            throw new IllegalArgumentException("unsupported language");
        }
        validateInteger(values, "security.password.min-length", 8, 128);
        validateInteger(values, "security.password.max-length", 8, 1024);
        int minimum = valueAsInt(values, "security.password.min-length", 8);
        int maximum = valueAsInt(values, "security.password.max-length", 128);
        if (minimum > maximum) throw new IllegalArgumentException("password length range is invalid");
        validateInteger(values, "session.timeout-seconds", 1, 2_592_000);
        validateInteger(values, "auth-timeout.timeout-seconds", 5, 3_600);
        validateInteger(values, "admin.temporary-password.length", 12, 64);
        validateInteger(values, "admin.temporary-password.expires-seconds", 60, 86_400);
        validateInteger(values, "notifications.login-failures.cooldown-seconds", 10, 86_400);
        validateInteger(values, "logging.audit.retention-days", 1, 3_650);
        validateInteger(values, "logging.audit.cleanup-interval-seconds", 60, 86_400);
        validateInteger(values, "database.postgresql.port", 1, 65_535);
        validateInteger(values, "database.postgresql.pool-size", 1, 64);
        validateInteger(values, "security.password.argon2.memory-kib", 8_192, 1_048_576);
        validateInteger(values, "security.password.argon2.iterations", 1, 20);
        validateInteger(values, "security.password.argon2.parallelism", 1, 16);
        validateInteger(values, "security.password.argon2.hash-length", 16, 64);
        validateInteger(values, "security.password.argon2.salt-length", 16, 64);
        validateInteger(values, "security.rate-limit.login.per-account.max-attempts", 1, 10_000);
        validateInteger(values, "security.rate-limit.login.per-account.window-seconds", 1, 86_400);
        validateInteger(values, "security.rate-limit.login.per-account.lock-seconds", 1, 604_800);
        validateInteger(values, "security.rate-limit.login.per-ip.max-attempts", 1, 100_000);
        validateInteger(values, "security.rate-limit.login.per-ip.window-seconds", 1, 86_400);
        validateInteger(values, "security.rate-limit.login.per-ip.lock-seconds", 1, 604_800);
        validateInteger(values, "security.registration.max-accounts-per-ip", 1, 1_000);
        validateInteger(values, "security.registration.max-attempts-per-hour", 1, 100_000);
        validateInteger(values, "redis.port", 1, 65_535);
        for (String operation : List.of("status", "doctor", "reload", "session", "audit",
                "unlock", "unregister", "resetpassword")) {
            validateInteger(values, "admin.fabric-permission-levels." + operation, 0, 4);
        }
        validateBoolean(values, "notifications.login-failures.enabled");
        validateBoolean(values, "notifications.discord.enabled");
        validateBoolean(values, "notifications.telegram.enabled");
        for (String path : List.of("session.enabled", "session.persistent", "session.verify-ip",
                "session.extend-while-online",
                "redis.enabled", "redis.required", "auth.online-mode.bypass-password", "auth-timeout.enabled",
                "logging.audit.enabled", "restrictions.freeze-player", "restrictions.allow-flight",
                "restrictions.block-chat", "restrictions.block-commands", "restrictions.block-inventory",
                "restrictions.block-interactions", "restrictions.block-block-break",
                "restrictions.block-block-place", "restrictions.block-damage-taken",
                "restrictions.block-damage-dealt", "restrictions.block-item-drop",
                "restrictions.block-item-pickup", "restrictions.block-hunger")) {
            validateBoolean(values, path);
        }
        for (String path : List.of("language", "database.type", "database.sqlite.file",
                "database.postgresql.host", "database.postgresql.database",
                "database.postgresql.username", "database.postgresql.password-env",
                "database.postgresql.ssl-mode", "security.ip-hashing.secret-env",
                "redis.uri-env", "redis.host", "notifications.discord.webhook-url",
                "notifications.discord.webhook-url-env", "notifications.telegram.bot-token",
                "notifications.telegram.chat-id", "notifications.telegram.bot-token-env",
                "notifications.telegram.chat-id-env")) {
            validateString(values, path);
        }
        Object allowedCommands = values.get("restrictions.allowed-commands");
        if (allowedCommands != null && (!(allowedCommands instanceof List<?> list)
                || !list.stream().allMatch(String.class::isInstance))) {
            throw new IllegalArgumentException("invalid allowed commands list");
        }
        String databaseType = valueAsString(values, "database.type", "sqlite").toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("sqlite", "postgresql", "postgres").contains(databaseType)) {
            throw new IllegalArgumentException("unsupported database type");
        }
        String sslMode = valueAsString(values, "database.postgresql.ssl-mode", "prefer")
                .toLowerCase(java.util.Locale.ROOT);
        if (!Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full").contains(sslMode)) {
            throw new IllegalArgumentException("unsupported PostgreSQL SSL mode");
        }
    }

    private void migrateNotificationSettings(File configFile, Map<String, Object> values,
                                             boolean sectionPresent) throws Exception {
        if (sectionPresent) return;
        String block = "\n\n# Security alerts. Passwords and raw IP addresses are never sent.\n"
                + "notifications:\n"
                + "  login-failures:\n"
                + "    enabled: false\n"
                + "    cooldown-seconds: 300\n"
                + "  discord:\n"
                + "    enabled: false\n"
                + "    webhook-url: \"\"\n"
                + "    webhook-url-env: \"${ENV:AUTHSECURED_DISCORD_WEBHOOK}\"\n"
                + "  telegram:\n"
                + "    enabled: false\n"
                + "    bot-token: \"\"\n"
                + "    chat-id: \"\"\n"
                + "    bot-token-env: \"${ENV:AUTHSECURED_TELEGRAM_BOT_TOKEN}\"\n"
                + "    chat-id-env: \"${ENV:AUTHSECURED_TELEGRAM_CHAT_ID}\"\n";
        java.nio.file.Path target = configFile.toPath();
        java.nio.file.Path temporary = target.resolveSibling(configFile.getName() + ".tmp");
        try {
            Files.writeString(temporary, Files.readString(target) + block);
            try {
                Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(target));
            } catch (UnsupportedOperationException ignored) {
                // POSIX permissions are unavailable on this filesystem.
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        values.put("notifications.login-failures.enabled", false);
        values.put("notifications.login-failures.cooldown-seconds", 300);
        values.put("notifications.discord.enabled", false);
        values.put("notifications.discord.webhook-url", "");
        values.put("notifications.discord.webhook-url-env", "${ENV:AUTHSECURED_DISCORD_WEBHOOK}");
        values.put("notifications.telegram.enabled", false);
        values.put("notifications.telegram.bot-token", "");
        values.put("notifications.telegram.chat-id", "");
        values.put("notifications.telegram.bot-token-env", "${ENV:AUTHSECURED_TELEGRAM_BOT_TOKEN}");
        values.put("notifications.telegram.chat-id-env", "${ENV:AUTHSECURED_TELEGRAM_CHAT_ID}");
        logger.info("Added Discord and Telegram notification settings to config.yml.");
    }

    private static void validateInteger(Map<String, Object> values, String path, int minimum, int maximum) {
        Object value = values.get(path);
        if (value != null && (!(value instanceof Number number)
                || number.longValue() < minimum || number.longValue() > maximum)) {
            throw new IllegalArgumentException("invalid integer value");
        }
    }

    private static void validateBoolean(Map<String, Object> values, String path) {
        Object value = values.get(path);
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException("invalid boolean value");
        }
    }

    private static void validateString(Map<String, Object> values, String path) {
        Object value = values.get(path);
        if (value != null && !(value instanceof String)) {
            throw new IllegalArgumentException("invalid string value");
        }
    }

    private static String valueAsString(Map<String, Object> values, String path, String fallback) {
        Object value = values.get(path);
        return value instanceof String string ? string : fallback;
    }

    private static int valueAsInt(Map<String, Object> values, String path, int fallback) {
        Object value = values.get(path);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private void saveDefaultIfNotExists(File targetFile, String resourceName) {
        if (!targetFile.exists()) {
            try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (in != null) {
                    Files.copy(in, targetFile.toPath());
                } else {
                    targetFile.createNewFile();
                }
            } catch (Exception e) {
                logger.warning("Could not create default file: " + resourceName);
            }
        }
    }

    public String getString(String path, String defaultValue) {
        Object val = getValue(path);
        if (val instanceof String str) {
            return resolveEnvVars(str);
        }
        return defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        Object val = getValue(path);
        if (val instanceof Number num) {
            return num.intValue();
        }
        return defaultValue;
    }

    public int getInt(String path, int defaultValue, int minimum, int maximum) {
        if (minimum > maximum || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Invalid numeric bounds for " + path);
        }
        int value = getInt(path, defaultValue);
        if (value < minimum || value > maximum) {
            logger.warning("Configuration value '" + path + "' must be between " + minimum
                    + " and " + maximum + "; using " + defaultValue + ".");
            return defaultValue;
        }
        return value;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object val = getValue(path);
        if (val instanceof Boolean b) {
            return b;
        }
        return defaultValue;
    }

    public List<String> getStringList(String path, List<String> defaultValue) {
        Object val = getValue(path);
        if (val instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) {
            return list.stream().map(String.class::cast).toList();
        }
        return defaultValue;
    }

    /**
     * The commands a player may run before login. {@code /2fa} is always included: a list saved by an older
     * version does not have it, and without it a player with an authenticator could never finish logging in.
     */
    public List<String> getAllowedCommands(List<String> defaultValue) {
        List<String> configured = getStringList("restrictions.allowed-commands", defaultValue);
        if (configured.stream().anyMatch("/2fa"::equalsIgnoreCase)) return configured;
        List<String> withTwoFactor = new java.util.ArrayList<>(configured);
        withTwoFactor.add("/2fa");
        return withTwoFactor;
    }

    public String getMessage(String path, String defaultValue) {
        return localizationManager.getMessage(path, defaultValue);
    }

    public String getMessage(String path, String defaultValue, Map<String, String> placeholders) {
        return localizationManager.getMessage(path, defaultValue, placeholders);
    }

    public LocalizationManager getLocalizationManager() {
        return localizationManager;
    }

    private Object getValue(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return configValues.get(path);
    }

    private static Map<String, Object> flatten(Map<?, ?> root) {
        Map<String, Object> flattened = new LinkedHashMap<>();
        flattenInto(root, "", flattened);
        return flattened;
    }

    private static void flattenInto(Map<?, ?> source, String prefix, Map<String, Object> target) {
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (!(entry.getKey() instanceof String key) || key.isBlank()) {
                throw new IllegalArgumentException("all YAML keys must be non-empty strings");
            }
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                flattenInto(nested, path, target);
            } else {
                target.put(path, value);
            }
        }
    }

    private static String resolveEnvVars(String value) {
        if (value.startsWith("${ENV:") && value.endsWith("}")) {
            String envVarName = value.substring(6, value.length() - 1);
            String envValue = System.getenv(envVarName);
            return envValue != null ? envValue : "";
        }
        return value;
    }
}
