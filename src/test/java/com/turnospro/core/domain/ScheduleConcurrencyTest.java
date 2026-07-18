package com.turnospro.core.domain;

import com.turnospro.core.exception.SlotAlreadyReservedException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScheduleConcurrencyTest {

    private TimeSlot timeSlot;
    private AtomicReference<Schedule> atomicSchedule;

    @BeforeEach
    void setUp() {

        // Define the target time slot for the concurrency collision test
        timeSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );

        // Thread-safe wrapper holding the master reference of our immutable aggregate.
        // It uses low-level hardware CAS (Compare-And-Swap) loops to guarantee atomicity.
        atomicSchedule = new AtomicReference<>(createNewSchedule(timeSlot));
    }


    @Test
    void shouldMaintainDomainInvariantsUnderMassiveMemoryStress() {

        // Gate synchronization barrier initialized to 1.
        // It prevents tasks from executing sequentially, ensuring an exact simultaneous burst.
        CountDownLatch latch = new CountDownLatch(1);

        // High-performance thread-safe metrics counters.
        // LongAdder avoids internal CAS contention under heavy concurrent load.
        LongAdder successfulReservations = new LongAdder();
        LongAdder businessCollisions = new LongAdder();

        System.out.println("=== Initializing Virtual Threads Allocation ===");

        // Variables to isolate execution performance window
        long startTime = 0;
        long endTime = 0;

        // Project Loom: Spin up an ExecutorService allocating one Virtual Thread per submitted task.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < 10000; i++) {
                final int taskId = i; // Effectively final variable to safely capture the current index inside the lambda

                executor.submit(() -> {
                    try {
                        // All virtual threads block here, waiting for the master gate to open
                        latch.await();

                        // Atomic retry loop: attempts to update the reference via CAS.
                        // If a collision occurs at hardware level, the lambda re-evaluates the domain
                        // logic against the newly updated state, triggering our business exception.
                        atomicSchedule.updateAndGet(currentSchedule -> currentSchedule.reserveSlot(timeSlot));

                        successfulReservations.increment();

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (SlotAlreadyReservedException e) {
                        // The domain invariant prevented a double booking safely.
                        businessCollisions.increment();
                    }
                });
            }

            // This releases the 10,000 threads simultaneously while the main thread waits at the auto-close block.
            System.out.println("\n[!] All tasks allocated. Opening gates! Releasing massive parallel burst...\n");
            startTime = System.nanoTime();
            latch.countDown();

        } // Try-with-resources auto-close barrier blocks execution here until all 10,000 virtual threads fully complete.

        endTime = System.nanoTime();

        // Calculate delta metrics
        long durationNs = endTime - startTime;
        double durationMs = durationNs / 1_000_000.0;

        // Print final execution metrics
        System.out.println("\n=== Stress Simulation Finished ===");
        System.out.println("Successful reservations: " + successfulReservations.sum());
        System.out.println("Collisions rejected safely: " + businessCollisions.sum());

        System.out.println("\n=== Infrastructure Performance Telemetry ===");
        System.out.println("Total execution time: " + String.format("%.2f", durationMs) + " ms");

        assertEquals(1, successfulReservations.sum(),
                "Only one reservation should have been successful");
        assertEquals(9999, businessCollisions.sum(),
                "The remaining 9999 threads should have failed via domain exception");


    }


    private Schedule createNewSchedule(TimeSlot timeSlot) {
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        TenantId tenantId = new TenantId("clinica-alfa");
        SequenceNumber sequenceNumber = new SequenceNumber(1L);
        Map<TimeSlot, SlotStatus> timeSlots = new HashMap<>();

        timeSlots.put(timeSlot, SlotStatus.AVAILABLE);

        return new Schedule(scheduleId, tenantId, sequenceNumber, timeSlots);
    }
}








