package com.turnospro.infrastructure.adapters.out.resilience;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Generic resilience engine designed to handle transient infrastructure failures
 * using Exponential Backoff with Full Jitter and dynamic Fail-Fast validation.
 */
public class ResilientExecutor {

    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;
    private final long timeoutLimitMs;

    public ResilientExecutor(int maxAttempts, long baseBackoffMs, long maxBackoffMs, long timeoutLimitMs) {
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
        this.timeoutLimitMs = timeoutLimitMs;
    }

    /**
     * Executes a functional operation with non-blocking retries under the metal of Virtual Threads.
     *
     * @param operation The lazy domain/infrastructure sequence placeholder to execute.
     * @param <T>       The generic target return type placeholder.
     * @return The successful evaluation result of the operation.
     */
    public <T> T executeWithRetry(Supplier<T> operation) {
        long startTime = System.currentTimeMillis();
        int attempt = 0;

        while (true) {
            try {
                attempt++;
                // Execution of the encapsulated sequence recipe
                return operation.get();

            } catch (Exception e) {
                // Determine if the failure is transient or fatal
                if (!isRetryable(e)) {
                    throw e; // Fatal Exception: Abort execution immediately to preserve domain truth
                }

                // Fail-Fast Principle: Validate if enough connection/HTTP time remains for the client
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (attempt >= maxAttempts || elapsedTime >= timeoutLimitMs) {
                    throw new RuntimeException("Resilience Threshold Exceeded. Max attempts reached or Timeout triggered (Fail-Fast).", e);
                }

                // Behavioral calculation of Exponential Backoff window sizing
                long calculatedBackoff = Math.min(maxBackoffMs, baseBackoffMs * (long) Math.pow(2, attempt - 1));

                // Full Jitter application to break up thundering herd synchronization
                long jitteredSleep = ThreadLocalRandom.current().nextLong(0, calculatedBackoff);

                try {
                    // Non-blocking wait: Virtual Thread unmounts from its Carrier Thread automatically
                    Thread.sleep(jitteredSleep);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Resilient execution interrupted during backoff sleep.", ie);
                }
            }
        }
    }

    private boolean isRetryable(Exception e) {
        // Specifically targeting the optimistic concurrency collision marker
        return e instanceof java.util.ConcurrentModificationException;
    }
}
