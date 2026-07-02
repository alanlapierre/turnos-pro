package schedule.infrastructure.simulation;


import schedule.domain.exceptions.SlotAlreadyReservedException;
import schedule.domain.models.TimeSlot;
import schedule.infrastructure.repository.ScheduleRepository;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;
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

        var repository = new ScheduleRepository(SCHEDULE_ID, TENANT_ID, timeSlot);

        // Gate synchronization barrier initialized to 1.
        // Prevents tasks from executing sequentially, ensuring an exact simultaneous burst.
        CountDownLatch latch = new CountDownLatch(1);

        // High-performance thread-safe metrics counters.
        // LongAdder avoids internal CAS contention under heavy concurrent load.
        LongAdder successfulReservations = new LongAdder();
        LongAdder businessCollisions = new LongAdder();
        LongAdder dbCollisions = new LongAdder();

        System.out.println("=== Initializing Virtual Threads Allocation ===");

        long startTime = 0;
        long endTime = 0;

        // Fetching the stale snapshot outside the loop to intentionally force DB level contention.
        var savedSchedule = repository.findById(SCHEDULE_ID);

        // Spin up an ExecutorService allocating one Virtual Thread per submitted task.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (int i = 0; i < 10000; i++) {
                final int taskId = i;

                executor.submit(() -> {
                    try {
                        System.out.println("Task [" + taskId + "] initialized and parked -> Executing on: " + Thread.currentThread());

                        // All virtual threads block here, waiting for the master gate to open
                        latch.await();

                        var modifiedSchedule = savedSchedule.reserveSlot(timeSlot);
                        repository.update(modifiedSchedule);

                        System.out.println("Task [" + taskId + "] SUCCESSFUL UPDATE -> Executing on: " + Thread.currentThread());
                        successfulReservations.increment();

                    } catch (InterruptedException e) {
                        System.out.println("Task [" + taskId + "] THREAD INTERRUPTED -> Executing on: " + Thread.currentThread());
                        Thread.currentThread().interrupt();
                    } catch (SlotAlreadyReservedException e) {
                        // The domain invariant prevented a double booking safely.
                        System.out.println("Task [" + taskId + "] BUSINESS COLLISION REJECTED -> Executing on: " + Thread.currentThread());
                        businessCollisions.increment();
                    } catch (ConcurrentModificationException e) {
                        // Optimistic lock failure triggered by the database update verdict (0 rows affected).
                        System.out.println("Task [" + taskId + "] DB COLLISION REJECTED -> Executing on: " + Thread.currentThread());
                        dbCollisions.increment();
                    } catch (Exception e) {
                        System.out.println("Task [" + taskId + "] UNEXPECTED ERROR -> Executing on: " + Thread.currentThread());
                        e.printStackTrace();
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
        System.out.println("DB Collisions rejected safely: " + dbCollisions.sum());

        System.out.println("\n=== Infrastructure Performance Telemetry ===");
        System.out.println("Total execution time: " + String.format("%.2f", durationMs) + " ms");
    }
}