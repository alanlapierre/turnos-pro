package com.turnospro.infrastructure.web;

import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TimeSlot;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
public class SlotController {

    private final ReserveSlotUseCase reserveSlotUseCase;

    // Spring Boot automatically injects the decorator defined in AppConfig
    public SlotController(ReserveSlotUseCase reserveSlotUseCase) {
        this.reserveSlotUseCase = reserveSlotUseCase;
    }

    @PostMapping("/{scheduleId}/reserve")
    public ResponseEntity<String> reserve(@PathVariable UUID scheduleId,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime) {
        // Executes pure domain logic wrapped in custom resiliency
        TimeSlot timeSlot = new TimeSlot(startTime, startTime.plusMinutes(30));
        reserveSlotUseCase.reserve(new ScheduleId(scheduleId), timeSlot);

        return ResponseEntity.ok("Reservation processed successfully");
    }
}
