package schedule.infrastructure;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class BaseIntegrationTest {

    /**
     * Singleton Container Pattern to share a single ephemeral database instance
     * across the entire test suite execution execution lifecycle.
     */
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("saas_test")
                    .withUsername("matrix_eng")
                    .withPassword("secret");

    protected static HikariDataSource dataSource;

    @BeforeAll
    public static void startCluster() {
        if (!postgres.isRunning()) {
            // Spin up the container instance on a dynamic, ephemeral host port
            postgres.start();

            // Initialize connection pool dynamically bound to the transient container credentials
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(postgres.getJdbcUrl());
            config.setUsername(postgres.getUsername());
            config.setPassword(postgres.getPassword());

            // Constrain resource allocation for local development boundaries
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);

            dataSource = new HikariDataSource(config);

            // Execute automated cold schema migrations against the freshly provisioned engine
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load();
            flyway.migrate();
        }
    }

    @AfterEach
    public void cleanState() {
        // Enforce atomic database truncation between tests to prevent State Pollution
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE schedules RESTART IDENTITY CASCADE;");
        } catch (SQLException e) {
            throw new RuntimeException("Critical: Failed to reset database clean state", e);
        }
    }
}
