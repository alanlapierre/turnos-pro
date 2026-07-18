package com.turnospro.infrastructure.adapters.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnospro.core.domain.*;
import com.turnospro.core.exception.ScheduleNotFoundException;
import com.turnospro.core.ports.out.ScheduleRepository;
import com.turnospro.infrastructure.adapters.out.persistence.exception.InfrastructureDatabaseException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class JdbcScheduleRepository implements ScheduleRepository {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public JdbcScheduleRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Optional<Schedule> findById(ScheduleId scheduleId) {
        String sql = "SELECT id, tenant_id, version, slots FROM schedules WHERE id = ?";

        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, scheduleId.id());

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

                    return Optional.of(new Schedule(new ScheduleId(UUID.fromString(id)), new TenantId(tenantId), new SequenceNumber(version), slots));
                }
            }

            throw new ScheduleNotFoundException("Schedule aggregate root not found for ID: " + scheduleId);

        } catch (SQLException e) {
            throw new InfrastructureDatabaseException("Database state modification failed", e);
        } catch (JsonProcessingException e) {
            throw new InfrastructureDatabaseException("JSON deserialization failed for slots map", e);
        }
    }

    @Override
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
            statement.setObject(2, schedule.scheduleId().id());
            statement.setLong(3, schedule.sequenceNumber().sequenceNumber());

            int rowsUpdated = statement.executeUpdate();

            // Atomic Relational CAS Check: If zero rows were altered, another thread changed the version
            if (rowsUpdated == 0) {
                throw new ConcurrentModificationException(
                        "Optimistic lock collision detected: The entity version changed under heavy concurrent write load.");
            }

        } catch (SQLException e) {
            throw new InfrastructureDatabaseException("Database state modification failed", e);
        } catch (JsonProcessingException e) {
            throw new InfrastructureDatabaseException("JSON serialization failed for slots map", e);
        }
    }

    @Override
    public void save(Schedule schedule) {
        String sql = """
            INSERT INTO schedules (id, tenant_id, version, slots)
            VALUES (?, ?, ?, ?::jsonb)
            ON CONFLICT (id) DO NOTHING;
            """;

        try (Connection connection = dataSource.getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            LocalDateTime start = schedule.timeSlots().keySet().stream().findFirst().get().start();
            SlotStatus status = schedule.timeSlots().values().stream().findFirst().get();

            UUID scheduleId = schedule.scheduleId().id();
            String tenantId = schedule.tenantId().id();

            Map<String, String> timeSlots = new HashMap<>();
            timeSlots.put(start.toString(), status.name());

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
            throw new InfrastructureDatabaseException("Critical failure during database seed initialization", e);
        }
    }

}
