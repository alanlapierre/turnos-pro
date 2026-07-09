package schedule.infrastructure.simulation;


import schedule.domain.exceptions.SlotAlreadyReservedException;
import schedule.domain.models.TimeSlot;
import schedule.domain.repository.ScheduleRepository;
import schedule.infrastructure.repository.JdbcScheduleRepository;
import schedule.infrastructure.repository.ResilientScheduleRepository;
import schedule.infrastructure.resilience.ResilientExecutor;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.LongAdder;

public class DBStressSimulator {

    public static final UUID SCHEDULE_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    public static final String TENANT_ID = "clinica-central";

    public static void main(String[] args) {

        var timeSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );

        // Core infrastructure components setup
        ScheduleRepository jdbcRepo = new JdbcScheduleRepository(SCHEDULE_ID, TENANT_ID, timeSlot);

        // Resilience policy parameters: maxAttempts=3, baseBackoffMs=10, maxBackoffMs=100, timeoutLimitMs=500
        ResilientExecutor resilientExecutor = new ResilientExecutor(3, 10, 100, 500);

        // Wrapping the raw JDBC repository using the functional Decorator Pattern
        ResilientScheduleRepository resilientRepo = new ResilientScheduleRepository(jdbcRepo, resilientExecutor);

        // Gate synchronization barrier initialized to 1.
        // Prevents tasks from executing sequentially, ensuring an exact simultaneous burst.
        CountDownLatch latch = new CountDownLatch(1);

        // High-performance thread-safe metrics counters.
        // LongAdder avoids internal CAS contention under heavy concurrent load.
        LongAdder successfulReservations = new LongAdder();
        LongAdder businessCollisions = new LongAdder();

        System.out.println("=== Initializing Virtual Threads Allocation ===");

        long startTime = 0;
        long endTime = 0;

        // Spin up an ExecutorService allocating one Virtual Thread per submitted task.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < 10000; i++) {
                final int taskId = i;

                executor.submit(() -> {
                    try {
                        System.out.println("Task [" + taskId + "] initialized and parked -> Executing on: " + Thread.currentThread());

                        // All virtual threads block here, waiting for the master gate to open
                        latch.await();

                        // Invoking the resilience engine by passing the domain business logic as an executable formula
                        resilientRepo.updateResiliently(SCHEDULE_ID, freshSchedule -> {
                            // The virtual thread evaluates this lazily on every fresh state reload loop iteration
                            return freshSchedule.reserveSlot(timeSlot);
                        });

                        System.out.println("Task [" + taskId + "] SUCCESSFUL UPDATE -> Executing on: " + Thread.currentThread());
                        successfulReservations.increment();

                    } catch (SlotAlreadyReservedException e) {
                        // The domain invariant prevented a double booking safely after a transparent recovery reload.
                        System.out.println("Task [" + taskId + "] BUSINESS COLLISION REJECTED -> Executing on: " + Thread.currentThread());
                        businessCollisions.increment();
                    } catch (Exception e) {
                        // Reaches here only if retry limits are exhausted or the Fail-Fast threshold is triggered
                        System.err.println("Task [" + taskId + "] CRITICAL RESILIENCE FAILURE: Reservation failed permanently. Reason: " + e.getMessage());
                    }
                });
            }

            System.out.println("\n[!] All tasks allocated. Opening gates! Releasing massive parallel burst...\n");
            startTime = System.nanoTime();
            latch.countDown();

        } // Try-with-resources blocks here until all 10,000 virtual threads fully complete.

        endTime = System.nanoTime();

        long durationNs = endTime - startTime;
        double durationMs = durationNs / 1_000_000.0;

        System.out.println("\n=== Stress Simulation Finished ===");
        System.out.println("Successful reservations: " + successfulReservations.sum());
        System.out.println("Business Collisions rejected safely: " + businessCollisions.sum());

        System.out.println("\n=== Infrastructure Performance Telemetry ===");
        System.out.println("Total execution time: " + String.format("%.2f", durationMs) + " ms");
    }
}