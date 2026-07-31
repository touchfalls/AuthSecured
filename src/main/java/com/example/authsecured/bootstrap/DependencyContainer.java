package com.example.authsecured.bootstrap;

import com.example.authsecured.application.*;
import com.example.authsecured.infrastructure.config.ConfigManager;
import com.example.authsecured.infrastructure.database.*;
import com.example.authsecured.infrastructure.redis.MemoryRateLimitStore;
import com.example.authsecured.infrastructure.redis.RedisRateLimitStore;
import com.example.authsecured.infrastructure.security.Argon2PasswordHasher;
import com.example.authsecured.infrastructure.security.IpHashService;
import com.example.authsecured.paper.player.PlayerRestrictionManager;
import com.example.authsecured.ports.*;
import org.bukkit.plugin.Plugin;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.util.logging.Logger;

public class DependencyContainer {

    private final Plugin plugin;
    private final Logger logger;
    private JedisPool jedisPool;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private IpHashService ipHashService;
    private PasswordHasher passwordHasher;
    private AccountRepository accountRepository;
    private SessionRepository sessionRepository;
    private AuditRepository auditRepository;
    private RateLimitStore rateLimitStore;
    private PasswordService passwordService;
    private RateLimitService rateLimitService;
    private SessionService sessionService;
    private SecurityService securityService;
    private RegistrationService registrationService;
    private LoginService loginService;
    private AuthService authService;
    private PlayerRestrictionManager restrictionManager;

    public DependencyContainer(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public void init() {
        this.configManager = new ConfigManager(plugin.getDataFolder(), logger);
        this.configManager.loadConfigurations();

        String ipSecret = configManager.getString("security.ip-hashing.secret-env", "");
        this.ipHashService = new IpHashService(ipSecret);

        String dbType = configManager.getString("database.type", "sqlite");
        String host = configManager.getString("database.postgresql.host", "localhost");
        int port = configManager.getInt("database.postgresql.port", 5432);
        String database = configManager.getString("database.postgresql.database", "authsecured");
        String username = configManager.getString("database.postgresql.username", "authsecured");
        String password = configManager.getString("database.postgresql.password-env", "");
        int poolSize = configManager.getInt("database.postgresql.pool-size", 10);
        String sqliteFile = new File(plugin.getDataFolder(), configManager.getString("database.sqlite.file", "auth.db")).getAbsolutePath();

        this.databaseManager = new DatabaseManager(logger, dbType, host, port, database, username, password, sqliteFile, poolSize);

        boolean isPostgres = databaseManager.isPostgres();
        if (isPostgres) {
            this.accountRepository = new PostgresAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
        } else {
            this.accountRepository = new SqliteAccountRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor());
        }

        this.sessionRepository = new DatabaseSessionRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor(), isPostgres);
        this.auditRepository = new DatabaseAuditRepository(databaseManager.getDataSource(), databaseManager.getDbExecutor(), isPostgres);

        int memKib = configManager.getInt("security.password.argon2.memory-kib", 65536);
        int iterations = configManager.getInt("security.password.argon2.iterations", 3);
        int parallelism = configManager.getInt("security.password.argon2.parallelism", 2);
        int hashLen = configManager.getInt("security.password.argon2.hash-length", 32);
        int saltLen = configManager.getInt("security.password.argon2.salt-length", 16);

        this.passwordHasher = new Argon2PasswordHasher(memKib, iterations, parallelism, hashLen, saltLen);

        boolean redisEnabled = configManager.getBoolean("redis.enabled", false);
        if (redisEnabled) {
            String redisUri = configManager.getString("redis.uri-env", "");
            String redisHost = configManager.getString("redis.host", "localhost");
            int redisPort = configManager.getInt("redis.port", 6379);
            try {
                if (redisUri != null && !redisUri.isBlank()) {
                    this.jedisPool = new JedisPool(redisUri);
                } else {
                    this.jedisPool = new JedisPool(redisHost, redisPort);
                }
                this.rateLimitStore = new RedisRateLimitStore(jedisPool, logger);
            } catch (Exception e) {
                logger.warning("Failed to initialize Redis pool, falling back to memory: " + e.getMessage());
                this.rateLimitStore = new MemoryRateLimitStore();
            }
        } else {
            this.rateLimitStore = new MemoryRateLimitStore();
        }

        int minLen = configManager.getInt("security.password.min-length", 10);
        int maxLen = configManager.getInt("security.password.max-length", 128);
        this.passwordService = new PasswordService(passwordHasher, minLen, maxLen, 4);

        int maxAccAttempts = configManager.getInt("security.rate-limit.login.per-account.max-attempts", 5);
        long accWindowSec = configManager.getInt("security.rate-limit.login.per-account.window-seconds", 600);
        long accLockSec = configManager.getInt("security.rate-limit.login.per-account.lock-seconds", 900);

        int maxIpAttempts = configManager.getInt("security.rate-limit.login.per-ip.max-attempts", 20);
        long ipWindowSec = configManager.getInt("security.rate-limit.login.per-ip.window-seconds", 600);
        long ipLockSec = configManager.getInt("security.rate-limit.login.per-ip.lock-seconds", 1800);

        int maxRegPerIp = configManager.getInt("security.registration.max-accounts-per-ip", 3);
        int maxRegPerHour = configManager.getInt("security.registration.max-attempts-per-hour", 10);

        this.rateLimitService = new RateLimitService(rateLimitStore, maxAccAttempts, accWindowSec, accLockSec,
                maxIpAttempts, ipWindowSec, ipLockSec, maxRegPerIp, maxRegPerHour);

        boolean persistentSession = configManager.getBoolean("session.persistent", false);
        long sessionTimeoutMin = configManager.getInt("session.timeout-minutes", 30);
        this.sessionService = new SessionService(sessionRepository, persistentSession, sessionTimeoutMin);

        boolean auditEnabled = configManager.getBoolean("logging.audit.enabled", true);
        this.securityService = new SecurityService(auditRepository, auditEnabled);

        this.registrationService = new RegistrationService(accountRepository, passwordService, rateLimitService, securityService);
        this.loginService = new LoginService(accountRepository, passwordService, rateLimitService, sessionService, securityService, maxAccAttempts, accLockSec);

        this.authService = new AuthService(registrationService, loginService, passwordService, sessionService, securityService, accountRepository);

        this.restrictionManager = new PlayerRestrictionManager();
    }

    public void reloadConfig() {
        if (configManager != null) {
            configManager.loadConfigurations();
        }
    }

    public void close() {
        if (passwordService != null) passwordService.shutdown();
        if (databaseManager != null) databaseManager.close();
        if (jedisPool != null && !jedisPool.isClosed()) jedisPool.close();
    }

    public ConfigManager getConfigManager() { return configManager; }
    public AuthService getAuthService() { return authService; }
    public PlayerRestrictionManager getRestrictionManager() { return restrictionManager; }
    public IpHashService getIpHashService() { return ipHashService; }
}
