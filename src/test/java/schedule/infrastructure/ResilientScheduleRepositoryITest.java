package schedule.infrastructure;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schedule.domain.exceptions.SlotAlreadyReservedException;
import schedule.domain.models.*;
import schedule.domain.types.SlotStatus;
import schedule.infrastructure.repository.JdbcScheduleRepository;
import schedule.infrastructure.repository.ResilientScheduleRepository;
import schedule.infrastructure.resilience.ResilientExecutor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class ResilientScheduleRepositoryITest extends BaseIntegrationTest {

    // Deterministic IDs for aggregate boundaries
    public static final UUID SCHEDULE_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final String TENANT_ID = "clinica-central";

    private ResilientScheduleRepository resilientRepository;

    @BeforeEach
    public void setup() {

        JdbcScheduleRepository jdbcRepo = new JdbcScheduleRepository(dataSource);

        // Custom Backoff configuration: 3 retries, 10ms initial delay, bounds between 100ms and 500ms
        ResilientExecutor executor = new ResilientExecutor(3, 10, 100, 500);

        this.resilientRepository = new ResilientScheduleRepository(jdbcRepo, executor);

        var timeSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );
        Map<TimeSlot, SlotStatus> timeSlotMap = new HashMap<>();
        timeSlotMap.put(timeSlot, SlotStatus.AVAILABLE);

        // Provision baseline snapshot state (Sequence / Version V1)
        Schedule initialSchedule = new Schedule(new ScheduleId(SCHEDULE_ID), new TenantId(TENANT_ID), new SequenceNumber(1L), timeSlotMap);
        jdbcRepo.save(initialSchedule);
    }

    @Test
    @DisplayName("Should self-heal database collisions and return safe business rejections using the Resilient Decorator")
    public void validateFullResilienceOnDatabase() throws InterruptedException {

        TimeSlot targetSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );

        // Gate synchronization barrier initialized to 1.
        // Prevents tasks from executing sequentially, ensuring an exact simultaneous burst.
        CountDownLatch latch = new CountDownLatch(1);

        // High-performance thread-safe metrics counters.
        // LongAdder avoids internal CAS contention under heavy concurrent load.
        LongAdder successfulReservations = new LongAdder();
        LongAdder businessCollisionsRejected = new LongAdder();
        LongAdder unexpectedErrors = new LongAdder();

        System.out.println("=== Initializing Virtual Threads Allocation ===");

        long startTime = 0;
        long endTime = 0;

        // Harness massive high-concurrency scale via standard JDK Virtual Threads
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < 10000; i++) {
                executor.submit(() -> {
                    try {
                        latch.await(); // Hold execution to force massive concurrency collision race condition

                        // Invoke resilient pipeline passing the business mutation routine closure
                        resilientRepository.updateResiliently(SCHEDULE_ID, freshSchedule ->
                                freshSchedule.reserveSlot(targetSlot)
                        );

                        successfulReservations.increment();
                    } catch (SlotAlreadyReservedException ex) {
                        // Capture predictable domain fatal business rule exception following a successful retry reload
                        businessCollisionsRejected.increment();
                    } catch (Exception e) {
                        // Any escaping database/optimistic lock technical exceptions count as failure
                        unexpectedErrors.increment();
                    }
                });
            }

            startTime = System.nanoTime();
            latch.countDown(); // Release the virtual thread stampede simultaneously
        } // Auto-close block implicitly triggers termination wait, establishing precise telemetry tracking

        endTime = System.nanoTime();

        long durationNs = endTime - startTime;
        double durationMs = durationNs / 1_000_000.0;

        System.out.println("\n=== Stress Simulation Finished ===");
        System.out.println("Successful reservations: " + successfulReservations.sum());
        System.out.println("Business Collisions rejected safely: " + businessCollisionsRejected.sum());
        System.out.println("Unexpected infrastructure errors: " + unexpectedErrors.sum());

        System.out.println("\n=== Infrastructure Performance Telemetry ===");
        System.out.println("Total execution time: " + String.format("%.2f", durationMs) + " ms");

        Assertions.assertEquals(1, successfulReservations.sum(),
                "Data integrity failure: Multiple race conditions managed to claim the exact same physical slot.");

    }
}
