package com.turnospro.infrastructure.web.exception;

import com.turnospro.core.exception.ScheduleNotFoundException;
import com.turnospro.core.exception.SlotAlreadyReservedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. Resource Not Found / Multi-Tenant Isolation (Returns HTTP 404 Not Found)
    @ExceptionHandler(ScheduleNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleScheduleNotFound(ScheduleNotFoundException ex) {
        var error = new ApiErrorResponse(
                "SCHEDULE_NOT_FOUND",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // 2. Pure Business Rejection Handling (Domain Invariant Rule) (Returns HTTP 409 Conflict)
    @ExceptionHandler(SlotAlreadyReservedException.class)
    public ResponseEntity<ApiErrorResponse> handleSlotAlreadyReserved(SlotAlreadyReservedException ex) {
        var error = new ApiErrorResponse(
                "SLOT_UNAVAILABLE",
                ex.getMessage(),
                HttpStatus.CONFLICT.value(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // 3. High Mass Contention Handling / Retry Exhaustion (Returns HTTP 429 Too Many Requests)
    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrencyCollision(ConcurrentModificationException ex) {
        var error = new ApiErrorResponse(
                "HIGH_TRAFFIC_CONTENTION",
                "The slot experienced extreme simultaneous demand. Please try again.",
                HttpStatus.TOO_MANY_REQUESTS.value(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(error);
    }

    // DTO Record to standardize JSON error responses
    public record ApiErrorResponse(
            String code,
            String message,
            int status,
            LocalDateTime timestamp
    ) {}
}
