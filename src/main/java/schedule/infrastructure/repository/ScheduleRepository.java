package schedule.infrastructure.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import schedule.domain.models.*;
import schedule.domain.types.SlotStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScheduleRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public ScheduleRepository(UUID scheduleId, String tenantId, TimeSlot timeSlot) {
        this.dataSource = createDataSource();
        this.objectMapper = new ObjectMapper();
        createTable();
        createInitialSchedule(scheduleId, tenantId, timeSlot);
    }

    public Schedule findById(UUID scheduleId) {
        String sql = "SELECT id, tenant_id, version, slots FROM schedules WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, scheduleId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    String id = resultSet.getString("id");
                    String tenantId = resultSet.getString("tenant_id");
                    long version = resultSet.getLong("version");
                    String slotsJson = resultSet.getString("slots");

                    // Deserialize JSONB to a safe primitive intermediate map
                    Map<String, String> rawSlots = objectMapper.readValue(slotsJson, new TypeReference<>() {});

                    // Reconstruct domain strongly-typed Value Objects via Stream pipeline
                    Map<TimeSlot, SlotStatus> slots = rawSlots.entrySet().stream()
                            .collect(Collectors.toUnmodifiableMap(
                                    entry -> {
                                        LocalDateTime start = LocalDateTime.parse(entry.getKey());
                                        LocalDateTime end = start.plusMinutes(30);
                                        return new TimeSlot(start, end);
                                    },
                                    entry -> SlotStatus.valueOf(entry.getValue())
                            ));

                    return new Schedule(new ScheduleId(id), new TenantId(tenantId), new SequenceNumber(version), slots);
                }
            }

            throw new IllegalArgumentException("Schedule aggregate root not found for ID: " + scheduleId);

        } catch (SQLException | JsonProcessingException e) {
            throw new RuntimeException("Critical infrastructure failure during findById execution", e);
        }
    }

    public void update(Schedule schedule) {
        String sql = "UPDATE schedules SET slots = ?::jsonb, version = version + 1 WHERE id = ? AND version = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            // Serialize domain Value Objects back to primitive text formats for JSONB storage
            Map<String, String> jsonSlotsMap = schedule.timeSlots().entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().start().toString(),
                            entry -> entry.getValue().name()
                    ));

            String jsonSlots = objectMapper.writeValueAsString(jsonSlotsMap);

            statement.setString(1, jsonSlots);
            statement.setObject(2, UUID.fromString(schedule.scheduleId().id()));
            statement.setLong(3, schedule.sequenceNumber().sequenceNumber());

            int rowsUpdated = statement.executeUpdate();

            // Atomic Relational CAS Check: If zero rows were altered, another thread changed the version
            if (rowsUpdated == 0) {
                throw new ConcurrentModificationException(
                        "Optimistic lock collision detected: The entity version changed under heavy concurrent write load.");
            }

        } catch (SQLException e) {
            System.err.println("Critical database layer error: " + e.getMessage());
            throw new RuntimeException("Database state modification failed", e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization failed for slots map", e);
        }
    }

    private void createInitialSchedule(UUID scheduleId, String tenantId, TimeSlot timeSlot) {
        String sql = """
            INSERT INTO schedules (id, tenant_id, version, slots)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING;
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            Map<String, String> timeSlots = new HashMap<>();
            timeSlots.put(timeSlot.start().toString(), SlotStatus.AVAILABLE.name());

            String slotsJson = objectMapper.writeValueAsString(timeSlots);

            stmt.setObject(1, scheduleId);
            stmt.setString(2, tenantId);
            stmt.setLong(3, 1L);
            stmt.setString(4, slotsJson);

            int rowsAffected = stmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("[INIT] Seed schedule inserted successfully. ID: " + scheduleId);
            } else {
                System.out.println("[INIT] Seed schedule already exists on disk. Skipping safely.");
            }

        } catch (Exception e) {
            throw new RuntimeException("Critical failure during database seed initialization", e);
        }
    }

    private void createTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS schedules (
                id UUID PRIMARY KEY,
                tenant_id VARCHAR(50) NOT NULL,
                version BIGINT NOT NULL,
                slots JSONB NOT NULL
            );
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("DDL execution failed: Unable to verify/create schedules table", e);
        }
    }

    private DataSource createDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5432/saas_db");
        config.setUsername("postgres");
        config.setPassword("secret");
        config.setMaximumPoolSize(10);

        return new HikariDataSource(config);
    }
}
