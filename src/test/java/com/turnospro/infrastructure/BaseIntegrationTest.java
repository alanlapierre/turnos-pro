package com.turnospro.infrastructure;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public abstract class BaseIntegrationTest {

    /**
     * Singleton Container Pattern to share a single ephemeral database instance
     * across the entire test suite execution lifecycle[cite: 3].
     */
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("saas_test")
                    .withUsername("matrix_eng")
                    .withPassword("secret");

    // Injects dynamic Docker host URL and ephemeral port into Spring Boot environment[cite: 3]
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // Injects the real DataSource configured by Spring Boot pointing to the container instance[cite: 3]
    @Autowired
    protected DataSource dataSource;

    @BeforeAll
    public static void startCluster() {
        if (!postgres.isRunning()) {
            // Spin up the container instance on a dynamic, ephemeral host port[cite: 3]
            postgres.start();
        }
    }

    @AfterEach
    public void cleanState() {
        // Enforce atomic database truncation between tests to prevent State Pollution[cite: 3]
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE schedules RESTART IDENTITY CASCADE;");
        } catch (SQLException e) {
            throw new RuntimeException("Critical: Failed to reset database clean state", e);
        }
    }
}
