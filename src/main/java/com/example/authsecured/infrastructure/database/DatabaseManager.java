package com.example.authsecured.infrastructure.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class DatabaseManager {

    private final Logger logger;
    private final String dbType;
    private HikariDataSource dataSource;
    private final ExecutorService dbExecutor;

    public DatabaseManager(Logger logger, String dbType, String host, int port, String database,
                           String username, String password, String sqlitePath, int poolSize) {
        this.logger = logger;
        this.dbType = dbType.toLowerCase();
        this.dbExecutor = Executors.newFixedThreadPool(Math.max(2, poolSize));
        initDataSource(host, port, database, username, password, sqlitePath, poolSize);
    }

    private void initDataSource(String host, int port, String database, String username,
                                String password, String sqlitePath, int poolSize) {
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(poolSize);
        config.setPoolName("AuthSecured-DB-Pool");

        if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
            config.setJdbcUrl(String.format("jdbc:postgresql://%s:%d/%s", host, port, database));
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("org.postgresql.Driver");
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        } else {
            // Default to SQLite
            config.setJdbcUrl("jdbc:sqlite:" + sqlitePath);
            config.setDriverClassName("org.sqlite.JDBC");
            config.setConnectionTestQuery("SELECT 1");
        }

        this.dataSource = new HikariDataSource(config);
        runMigrations();
    }

    public void runMigrations() {
        try {
            if ("postgresql".equals(dbType) || "postgres".equals(dbType)) {
                Flyway flyway = Flyway.configure(getClass().getClassLoader())
                        .dataSource(dataSource)
                        .locations("classpath:db/migration")
                        .load();
                flyway.migrate();
                logger.info("PostgreSQL database migrations applied successfully via Flyway.");
            } else {
                // SQLite migration
                try (Connection conn = dataSource.getConnection();
                     Statement stmt = conn.createStatement()) {
                    InputStream is = getClass().getResourceAsStream("/db/migration/V1__initial_sqlite_schema.sql");
                    if (is != null) {
                        String sql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                                .lines().collect(Collectors.joining("\n"));
                        for (String query : sql.split(";")) {
                            if (!query.trim().isEmpty()) {
                                stmt.execute(query.trim());
                            }
                        }
                        logger.info("SQLite database schema initialized successfully.");
                    }
                }
            }
        } catch (Exception e) {
            logger.severe("Database migration failure: " + e.getMessage());
            throw new RuntimeException("Failed to run database migrations", e);
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

    public void close() {
        if (dbExecutor != null && !dbExecutor.isShutdown()) {
            dbExecutor.shutdown();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
