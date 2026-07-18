package com.turnospro.core.infrastructure;

import com.turnospro.core.application.ScheduleService;
import com.turnospro.core.domain.*;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.core.ports.out.ScheduleRepository;
import com.turnospro.infrastructure.adapters.in.ResilientReserveSlotUseCaseDecorator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.turnospro.core.exception.SlotAlreadyReservedException;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
import com.turnospro.infrastructure.adapters.out.resilience.ResilientExecutor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResilientScheduleRepositoryITest extends BaseIntegrationTest {

    // Deterministic IDs for aggregate boundaries
    public static final UUID SCHEDULE_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final String TENANT_ID = "clinica-central";

    private ReserveSlotUseCase reserveSlotUseCase;

    @BeforeEach
    public void setup() {
        // Instantiate Driving and Driven components manually outside any framework container
        ScheduleRepository jdbcRepo = new JdbcScheduleRepository(dataSource);
        ReserveSlotUseCase baseUseCase = new ScheduleService(jdbcRepo);

        // Define retry budget, backoff bounds, and structural isolation parameters
        ResilientExecutor executor = new ResilientExecutor(4, 10, 300, 5000);

        // Intercept execution flows wrapping the flat service via the Inbound Decorator pattern
        this.reserveSlotUseCase = new ResilientReserveSlotUseCaseDecorator(baseUseCase, executor);

        var timeSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );
        Map<TimeSlot, SlotStatus> timeSlotMap = new HashMap<>();
        timeSlotMap.put(timeSlot, SlotStatus.AVAILABLE);

        // Provision baseline snapshot state (Sequence / Version V1) inside the real database instance
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

                        // Invoke resilient pipeline
                        reserveSlotUseCase.reserve(new ScheduleId(SCHEDULE_ID), targetSlot);

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

        // Assert ultimate data consistency and operational integrity
        assertEquals(1, successfulReservations.sum(),
                "Only one reservation should have been successful");

        assertEquals(9999, businessCollisionsRejected.sum(),
                "The remaining 9,999 threads should have hit safe domain rejections after retry reload");

        assertEquals(0, unexpectedErrors.sum(),
                "Zero raw optimistic lock or database exceptions should escape the resilient boundary");
    }
}
