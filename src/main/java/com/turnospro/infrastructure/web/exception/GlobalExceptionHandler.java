package com.turnospro.infrastructure.web.exception;

import com.turnospro.core.exception.SlotAlreadyReservedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.ConcurrentModificationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // Pure Business Rejection Handling (Domain Rule)
    @ExceptionHandler(SlotAlreadyReservedException.class)
    public ResponseEntity<ApiErrorResponse> handleSlotAlreadyReserved(SlotAlreadyReservedException ex) {
        var error = new ApiErrorResponse(
                "SLOT_UNAVAILABLE",
                ex.getMessage(),
                HttpStatus.CONFLICT.value(),
                LocalDateTime.now()
        );
        // Returns HTTP 409 Conflict
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    // High Mass Contention Handling / Retry Exhaustion (Escaped Technical Failure)
    @ExceptionHandler(ConcurrentModificationException.class)
    public ResponseEntity<ApiErrorResponse> handleConcurrencyCollision(ConcurrentModificationException ex) {
        var error = new ApiErrorResponse(
                "HIGH_TRAFFIC_CONTENTION",
                "The slot experienced extreme simultaneous demand. Please try again.",
                HttpStatus.TOO_MANY_REQUESTS.value(), // or HttpStatus.CONFLICT (409)
                LocalDateTime.now()
        );
        // Returns HTTP 429 Too Many Requests (Traffic contention SLA)
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
