package io.github.touchfalls.authsecured.infrastructure.database;

import io.github.touchfalls.authsecured.ports.PlatformLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class DatabaseManager {

    private final PlatformLogger logger;
    private final String dbType;
    private HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public DatabaseManager(PlatformLogger logger, String dbType, String host, int port, String database,
                           String username, String password, String sqlitePath, int poolSize) {
        this(logger, dbType, host, port, database, username, password, sqlitePath, poolSize, "prefer");
    }

    public DatabaseManager(PlatformLogger logger, String dbType, String host, int port, String database,
                           String username, String password, String sqlitePath, int poolSize,
                           String sslMode) {
        this.logger = logger;
        this.dbType = dbType.toLowerCase(Locale.ROOT);
        AtomicInteger threadNumber = new AtomicInteger();
        // Not daemon threads: queued writes such as audit records still finish during shutdown.
        this.dbExecutor = Executors.newFixedThreadPool(Math.max(2, poolSize), runnable ->
                new Thread(runnable, "authsecured-database-" + threadNumber.incrementAndGet()));
        initDataSource(host, port, database, username, password, sqlitePath, poolSize, sslMode);
    }

    private void initDataSource(String host, int port, String database, String username,
                                String password, String sqlitePath, int poolSize, String sslMode) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("AuthSecured-DB-Pool");

        if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.addDataSourceProperty("sslmode", normalizeSslMode(sslMode));
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionInitSql("PRAGMA foreign_keys = ON");
            // The pool has several connections: WAL lets readers run beside a writer, and the timeout
            // makes a second writer wait instead of failing at once with "database is locked".
            config.addDataSourceProperty("journal_mode", "WAL");
            config.addDataSourceProperty("busy_timeout", "5000");
            config.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(config);
        try {
            runMigrations();
        } catch (RuntimeException exception) {
            // Nothing holds a reference to this half-constructed DatabaseManager once the
            // constructor throws, so without this the pool's connections stay open forever.
            dataSource.close();
            dbExecutor.shutdownNow();
            throw exception;
        }
    }

    private String normalizeSslMode(String sslMode) {
        String normalized = sslMode == null ? "prefer" : sslMode.toLowerCase(Locale.ROOT);
        if (Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full")
                .contains(normalized)) {
            return normalized;
        }
        logger.warning("Unsupported PostgreSQL ssl-mode '" + sslMode + "'; using prefer.");
        return "prefer";
    }

    public void runMigrations() {
        try {
            if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
                Flyway flyway = Flyway.configure(DatabaseManager.class.getClassLoader())
                        .dataSource(dataSource)
                        .locations("classpath:db/migration/postgresql")
                        .load();
                flyway.migrate();
                logger.info("PostgreSQL database migrations applied successfully via Flyway.");
            } else {
                try (Connection conn = dataSource.getConnection()) {
                    migrateSqlite(conn);
                    logger.info("SQLite database schema is at version 4.");
                }
            }
        } catch (Exception e) {
            logger.severe("Database migration failed. Connection details are hidden.");
            throw new RuntimeException("Failed to run database migrations", e);
        }
    }

    private void migrateSqlite(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS authsecured_schema_version "
                        + "(id INTEGER PRIMARY KEY CHECK(id = 1), version INTEGER NOT NULL)");
            }
            int version = schemaVersion(connection);
            if (version == 0) {
                try (InputStream script = getClass().getResourceAsStream(
                        "/db/migration/V1__initial_sqlite_schema.sql")) {
                    if (script == null) throw new IllegalStateException("SQLite V1 migration is missing");
                    SqliteScriptExecutor.execute(connection,
                            new String(script.readAllBytes(), StandardCharsets.UTF_8));
                }
                setSchemaVersion(connection, 1);
                version = 1;
            }
            if (version == 1) {
                applyAccountSecurityColumnsSqlite(connection);
                setSchemaVersion(connection, 2);
                version = 2;
            }
            if (version == 2) {
                applyRegistrationIpHashColumnSqlite(connection);
                setSchemaVersion(connection, 3);
                version = 3;
            }
            if (version == 3) {
                if (!hasColumn(connection, "accounts", "last_login_ip_hash")) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE accounts ADD COLUMN last_login_ip_hash BLOB NULL");
                    }
                }
                setSchemaVersion(connection, 4);
            } else if (version > 4) {
                throw new IllegalStateException("SQLite schema is newer than this AuthSecured version");
            }
            connection.commit();
        } catch (Exception exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT version FROM authsecured_schema_version")) {
            return rows.next() ? rows.getInt(1) : 0;
        }
    }

    private static void setSchemaVersion(Connection connection, int version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO authsecured_schema_version(id, version) VALUES (1, ?) "
                        + "ON CONFLICT(id) DO UPDATE SET version = excluded.version")) {
            statement.setInt(1, version);
            statement.executeUpdate();
        }
    }

    /** Adds columns introduced after the first public schema without breaking existing SQLite installs. */
    private void applyAccountSecurityColumnsSqlite(Connection connection) throws Exception {
        if (!hasColumn(connection, "accounts", "password_change_required")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE accounts ADD COLUMN password_change_required INTEGER NOT NULL DEFAULT 0");
            }
        }
        if (!hasColumn(connection, "accounts", "temporary_password_expires_at")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE accounts ADD COLUMN temporary_password_expires_at TEXT NULL");
            }
        }
    }

    /** Lets the per-IP registration cap survive disabled audit logging or audit retention. */
    private void applyRegistrationIpHashColumnSqlite(Connection connection) throws Exception {
        if (!hasColumn(connection, "accounts", "registration_ip_hash")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE accounts ADD COLUMN registration_ip_hash BLOB NULL");
            }
        }
        // Best-effort backfill from existing audit history; rows with no matching event
        // (audit disabled, or purged by retention) stay NULL and no longer count toward
        // security.registration.max-accounts-per-ip until the account registers again.
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                    "UPDATE accounts SET registration_ip_hash = ("
                            + "SELECT ip_hash FROM security_events "
                            + "WHERE security_events.account_id = accounts.id "
                            + "AND event_type = 'REGISTER_SUCCESS' AND ip_hash IS NOT NULL "
                            + "ORDER BY created_at ASC LIMIT 1) "
                            + "WHERE registration_ip_hash IS NULL");
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE INDEX IF NOT EXISTS idx_accounts_registration_ip_hash "
                    + "ON accounts(registration_ip_hash)");
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws Exception {
        DatabaseMetaData metadata = connection.getMetaData();
        try (var columns = metadata.getColumns(null, null, table, column)) {
            return columns.next();
        }
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    public boolean isPostgres() {
        return "postgresql".equals(dbType) || "postgres".equals(dbType);
    }

    public CompletableFuture<Boolean> ping() {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("SELECT 1");
                return true;
            } catch (Exception exception) {
                return false;
            }
        }, dbExecutor);
    }

    public String getDatabaseType() {
        return isPostgres() ? "postgresql" : "sqlite";
    }

    public int getActiveConnections() {
        return dataSource.getHikariPoolMXBean() == null ? -1
                : dataSource.getHikariPoolMXBean().getActiveConnections();
    }

    public int getIdleConnections() {
        return dataSource.getHikariPoolMXBean() == null ? -1
                : dataSource.getHikariPoolMXBean().getIdleConnections();
    }

    public void close() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
            try {
                // Queued writes such as audit records need the pool, so it closes only after them.
                if (!dbExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    logger.warning("Database writes still pending after 5 seconds were dropped at shutdown.");
                    dbExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                dbExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
