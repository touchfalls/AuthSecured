package io.github.touchfalls.authsecured.bootstrap;

import io.github.touchfalls.authsecured.application.AuthService;
import io.github.touchfalls.authsecured.application.LoginService;
import io.github.touchfalls.authsecured.application.PasswordService;
import io.github.touchfalls.authsecured.application.TwoFactorService;
import io.github.touchfalls.authsecured.application.RateLimitService;
import io.github.touchfalls.authsecured.application.RegistrationService;
import io.github.touchfalls.authsecured.application.SecurityService;
import io.github.touchfalls.authsecured.application.SessionService;
import io.github.touchfalls.authsecured.application.UnifiedCommandServiceImpl;
import io.github.touchfalls.authsecured.application.AuthenticationTimeoutService;
import io.github.touchfalls.authsecured.infrastructure.config.ConfigManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseAuditRepository;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseManager;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseSessionRepository;
import io.github.touchfalls.authsecured.infrastructure.database.DatabaseTotpRepository;
import io.github.touchfalls.authsecured.infrastructure.security.TotpService;
import io.github.touchfalls.authsecured.ports.TotpRepository;
import io.github.touchfalls.authsecured.infrastructure.database.PostgresAccountRepository;
import io.github.touchfalls.authsecured.infrastructure.database.SqliteAccountRepository;
import io.github.touchfalls.authsecured.infrastructure.redis.MemoryRateLimitStore;
import io.github.touchfalls.authsecured.infrastructure.redis.RedisRateLimitStore;
import io.github.touchfalls.authsecured.infrastructure.security.Argon2PasswordHasher;
import io.github.touchfalls.authsecured.infrastructure.security.IpHashService;
import io.github.touchfalls.authsecured.infrastructure.notification.WebhookSecurityNotifier;
import io.github.touchfalls.authsecured.ports.AccountRepository;
import io.github.touchfalls.authsecured.ports.AuditRepository;
import io.github.touchfalls.authsecured.ports.AuthPlatform;
import io.github.touchfalls.authsecured.ports.PasswordHasher;
import io.github.touchfalls.authsecured.ports.PlatformLogger;
import io.github.touchfalls.authsecured.ports.RateLimitStore;
import io.github.touchfalls.authsecured.ports.SessionRepository;
import io.github.touchfalls.authsecured.ports.UnifiedCommandExecutor;
import io.github.touchfalls.authsecured.ports.SecurityNotifier;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Builds the platform-neutral authentication graph shared by Paper and Fabric.
 */
public final class DependencyContainer implements AutoCloseable {

    private final AuthPlatform platform;
    private JedisPool jedisPool;
    private ExecutorService redisExecutor;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private IpHashService ipHashService;
    private PasswordService passwordService;
    private SessionService sessionService;
    private AuthService authService;
    private SecurityService securityService;
    private MaintenanceService maintenanceService;
    private AuthenticationTimeoutService authenticationTimeoutService;
    private UnifiedCommandExecutor commandExecutor;
    private String rateLimitBackend = "memory";
    private RedisRateLimitStore redisRateLimitStore;
    private boolean ipHashSecretFromEnvironment;

    public DependencyContainer(AuthPlatform platform) {
        this.platform = platform;
    }

    public void init() {
        File dataFolder = platform.getDataFolder();
        PlatformLogger logger = platform.getLogger();

        this.configManager = new ConfigManager(dataFolder, logger);
        if (!this.configManager.loadConfigurations()) {
            throw new IllegalStateException("AuthSecured configuration is invalid");
        }

        IpSecretStore.Secret ipHashSecret = IpSecretStore.loadOrCreate(dataFolder,
                configManager.getString("security.ip-hashing.secret-env", ""), logger);
        this.ipHashSecretFromEnvironment = ipHashSecret.fromEnvironment();
        this.ipHashService = new IpHashService(ipHashSecret.value(), logger);

        String dbType = configManager.getString("database.type", "sqlite").toLowerCase(Locale.ROOT);
        if (!Set.of("sqlite", "postgresql", "postgres").contains(dbType)) {
            logger.warning("Unsupported database.type '" + dbType + "'; using sqlite.");
            dbType = "sqlite";
        }
        String host = configManager.getString("database.postgresql.host", "localhost");
        int port = configManager.getInt("database.postgresql.port", 5432, 1, 65535);
        String database = configManager.getString("database.postgresql.database", "authsecured");
        String username = configManager.getString("database.postgresql.username", "authsecured");
        String password = configManager.getString("database.postgresql.password-env", "");
        String sslMode = configManager.getString("database.postgresql.ssl-mode",
                configManager.getBoolean("database.postgresql.ssl", false) ? "require" : "prefer");
        int poolSize = configManager.getInt("database.postgresql.pool-size", 10, 1, 64);
        String sqliteFile = new File(dataFolder,
                configManager.getString("database.sqlite.file", "auth.db")).getAbsolutePath();

        this.databaseManager = new DatabaseManager(logger, dbType, host, port, database, username,
                password, sqliteFile, poolSize, sslMode);

        boolean postgres = databaseManager.isPostgres();
        AccountRepository accountRepository = postgres
                ? new PostgresAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor())
                : new SqliteAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
        SessionRepository sessionRepository = new DatabaseSessionRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres);
        AuditRepository auditRepository = new DatabaseAuditRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres, logger);

        PasswordHasher passwordHasher = new Argon2PasswordHasher(
                configManager.getInt("security.password.argon2.memory-kib", 65536, 8192, 1_048_576),
                configManager.getInt("security.password.argon2.iterations", 3, 1, 20),
                configManager.getInt("security.password.argon2.parallelism", 2, 1, 16),
                configManager.getInt("security.password.argon2.hash-length", 32, 16, 64),
                configManager.getInt("security.password.argon2.salt-length", 16, 16, 64), logger);

        RateLimitStore rateLimitStore = createRateLimitStore(logger);
        int minLength = configManager.getInt("security.password.min-length", 8, 8, 128);
        int maxLength = configManager.getInt("security.password.max-length", 128, minLength, 1024);
        this.passwordService = new PasswordService(passwordHasher, minLength, maxLength, 4);

        int maxAccountAttempts = configManager.getInt(
                "security.rate-limit.login.per-account.max-attempts", 5, 1, 10_000);
        long accountWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.window-seconds", 600, 1, 86_400);
        long accountLockSeconds = configManager.getInt(
                "security.rate-limit.login.per-account.lock-seconds", 900, 1, 604_800);
        int maxIpAttempts = configManager.getInt(
                "security.rate-limit.login.per-ip.max-attempts", 20, 1, 100_000);
        long ipWindowSeconds = configManager.getInt(
                "security.rate-limit.login.per-ip.window-seconds", 600, 1, 86_400);
        long ipLockSeconds = configManager.getInt(
                "security.rate-limit.login.per-ip.lock-seconds", 1800, 1, 604_800);

        RateLimitService rateLimitService = new RateLimitService(
                rateLimitStore,
                maxAccountAttempts,
                accountWindowSeconds,
                accountLockSeconds,
                maxIpAttempts,
                ipWindowSeconds,
                ipLockSeconds,
                configManager.getInt("security.registration.max-accounts-per-ip", 3, 1, 1_000),
                configManager.getInt("security.registration.max-attempts-per-hour", 10, 1, 100_000));

        long sessionTimeoutSeconds = configManager.getInt(
                "session.timeout-seconds", 1800, 1, 2_592_000);
        this.sessionService = new SessionService(
                sessionRepository,
                configManager.getBoolean("session.enabled", true),
                configManager.getBoolean("session.persistent", true),
                sessionTimeoutSeconds,
                configManager.getBoolean("session.verify-ip", true));
        this.sessionService.setExtendWhileOnline(configManager.getBoolean("session.extend-while-online", false));
        this.sessionService.initialize().join();

        this.securityService = new SecurityService(
                auditRepository, configManager.getBoolean("logging.audit.enabled", true));
        this.maintenanceService = new MaintenanceService(sessionService, securityService, logger,
                configManager.getInt("logging.audit.cleanup-interval-seconds", 3_600, 60, 86_400),
                configManager.getInt("logging.audit.retention-days", 30, 1, 3_650),
                () -> platform.getRestrictionAdapter().getAuthenticatedPlayers());
        SecurityNotifier securityNotifier = createSecurityNotifier(logger);
        RegistrationService registrationService = new RegistrationService(
                accountRepository, passwordService, rateLimitService, securityService, logger);
        LoginService loginService = new LoginService(
                accountRepository,
                passwordService,
                rateLimitService,
                sessionService,
                securityService,
                maxAccountAttempts,
                accountLockSeconds,
                securityNotifier, logger);

        this.authenticationTimeoutService = new AuthenticationTimeoutService(
                configManager.getBoolean("auth-timeout.enabled", true),
                configManager.getInt("auth-timeout.timeout-seconds", 60, 5, 3_600));

        this.authService = new AuthService(registrationService, loginService, passwordService,
                sessionService, securityService, accountRepository, logger);
        TotpRepository totpRepository = new DatabaseTotpRepository(
                databaseManager.getDataSource(), databaseManager.getDbExecutor(), postgres);
        TotpService totpService = new TotpService(
                ipHashService.deriveKey("totp-encryption"), ipHashService.deriveKey("totp-backup"));
        loginService.enableTwoFactor(totpRepository);
        authService.setTwoFactorService(new TwoFactorService(accountRepository, totpRepository, totpService,
                rateLimitService, securityService, loginService, maxAccountAttempts, accountLockSeconds));
        this.commandExecutor = new UnifiedCommandServiceImpl(authService, platform,
                configManager.getLocalizationManager(), ipHashService, authenticationTimeoutService);
    }

    private RateLimitStore createRateLimitStore(PlatformLogger logger) {
        if (!configManager.getBoolean("redis.enabled", false)) {
            this.rateLimitBackend = "memory";
            return new MemoryRateLimitStore();
        }

        String redisUri = configManager.getString("redis.uri-env", "");
        String redisHost = configManager.getString("redis.host", "localhost");
        int redisPort = configManager.getInt("redis.port", 6379, 1, 65535);
        boolean required = configManager.getBoolean("redis.required", false);
        try {
            this.jedisPool = redisUri != null && !redisUri.isBlank()
                    ? new JedisPool(redisUri)
                    : new JedisPool(redisHost, redisPort);
            try (var jedis = jedisPool.getResource()) {
                if (!"PONG".equalsIgnoreCase(jedis.ping())) {
                    throw new IllegalStateException("Redis ping failed");
                }
            }
            this.rateLimitBackend = "redis";
            this.redisExecutor = Executors.newFixedThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "authsecured-redis");
                thread.setDaemon(true);
                return thread;
            });
            this.redisRateLimitStore = new RedisRateLimitStore(jedisPool, logger, redisExecutor, required);
            return redisRateLimitStore;
        } catch (RuntimeException exception) {
            if (jedisPool != null) {
                jedisPool.close();
                jedisPool = null;
            }
            if (required) {
                logger.severe("Required Redis is unavailable. AuthSecured initialization stopped. Connection details are hidden.");
                throw new IllegalStateException("Required Redis is unavailable");
            }
            logger.severe("Redis is unavailable; rate limits use local memory only. Connection details are hidden.");
            this.rateLimitBackend = "memory-fallback";
            return new MemoryRateLimitStore();
        }
    }

    private SecurityNotifier createSecurityNotifier(PlatformLogger logger) {
        if (!configManager.getBoolean("notifications.login-failures.enabled", false)) {
            return SecurityNotifier.NOOP;
        }
        String discord = configManager.getBoolean("notifications.discord.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.discord.webhook-url", ""),
                configManager.getString("notifications.discord.webhook-url-env", "")) : "";
        String telegramToken = configManager.getBoolean("notifications.telegram.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.telegram.bot-token", ""),
                configManager.getString("notifications.telegram.bot-token-env", "")) : "";
        String telegramChat = configManager.getBoolean("notifications.telegram.enabled", true)
                ? firstNonBlank(configManager.getString("notifications.telegram.chat-id", ""),
                configManager.getString("notifications.telegram.chat-id-env", "")) : "";
        return new WebhookSecurityNotifier(discord, telegramToken, telegramChat,
                configManager.getInt("notifications.login-failures.cooldown-seconds", 300, 10, 86_400),
                logger);
    }

    public boolean reloadConfig() {
        if (configManager != null) {
            if (!configManager.loadConfigurations()) return false;
            if (sessionService != null) {
                sessionService.setTimeoutSeconds(configManager.getInt(
                        "session.timeout-seconds", (int) sessionService.getTimeoutSeconds(), 1, 2_592_000));
                sessionService.setExtendWhileOnline(configManager.getBoolean(
                        "session.extend-while-online", sessionService.isExtendWhileOnline()));
            }
            if (authenticationTimeoutService != null) {
                authenticationTimeoutService.configure(
                        configManager.getBoolean("auth-timeout.enabled", authenticationTimeoutService.isEnabled()),
                        configManager.getInt("auth-timeout.timeout-seconds",
                                (int) authenticationTimeoutService.getTimeoutSeconds(), 5, 3_600));
            }
            if (maintenanceService != null) {
                maintenanceService.configure(
                        configManager.getInt("logging.audit.cleanup-interval-seconds", 3_600, 60, 86_400),
                        configManager.getInt("logging.audit.retention-days", 30, 1, 3_650));
            }
            return true;
        }
        return false;
    }

    @Override
    public void close() {
        if (maintenanceService != null) {
            maintenanceService.close();
        }
        if (passwordService != null) {
            passwordService.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
        if (redisExecutor != null) {
            redisExecutor.shutdownNow();
        }
    }

    public AuthPlatform getPlatform() {
        return platform;
    }

    public MaintenanceService getMaintenanceService() {
        return maintenanceService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public AuthService getAuthService() {
        return authService;
    }

    public UnifiedCommandExecutor getCommandExecutor() {
        return commandExecutor;
    }

    public IpHashService getIpHashService() {
        return ipHashService;
    }

    public SessionService getSessionService() {
        return sessionService;
    }

    public AuthenticationTimeoutService getAuthenticationTimeoutService() {
        return authenticationTimeoutService;
    }

    public SecurityService getSecurityService() {
        return securityService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public String getRateLimitBackend() {
        return redisRateLimitStore != null && redisRateLimitStore.isDegraded()
                ? "memory-fallback" : rateLimitBackend;
    }

    public boolean isIpHashSecretFromEnvironment() {
        return ipHashSecretFromEnvironment;
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : (fallback == null ? "" : fallback);
    }
}
