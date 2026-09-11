package com.turnospro.infrastructure;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;

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

    // Ephemeral LocalStack container pinned to the tested 3.4 version
    protected static final LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4"))
                    .withServices(LocalStackContainer.Service.SQS)
                    .withEnv("ACTIVATE_PRO", "0");

    // Injects dynamic Docker host URL and ephemeral port into Spring Boot environment[cite: 3]
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Dynamic injection of the ephemeral LocalStack container for Spring Boot
        registry.add("aws.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.SQS).toString());
        registry.add("aws.region", localstack::getRegion);
        registry.add("aws.access-key", localstack::getAccessKey);
        registry.add("aws.secret-key", localstack::getSecretKey);
        registry.add("aws.sqs.queue-url", () -> queueUrl);
    }

    // Injects the real DataSource configured by Spring Boot pointing to the container instance[cite: 3]
    @Autowired
    protected DataSource dataSource;

    protected static String queueUrl;

    @BeforeAll
    public static void startContainers() {
        if (!postgres.isRunning()) {
            // Spin up the container instance on a dynamic, ephemeral host port[cite: 3]
            postgres.start();
        }

        if (!localstack.isRunning()) {
            localstack.start();

            // Create the SQS client pointing to the ephemeral container to provision the test queue
            try (SqsClient adminClient = SqsClient.builder()
                    .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.SQS))
                    .region(Region.of(localstack.getRegion()))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                    .build()) {

                queueUrl = adminClient.createQueue(CreateQueueRequest.builder()
                        .queueName("slot-reserved-test-queue")
                        .build()).queueUrl();
            }
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
