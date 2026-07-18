package com.turnospro.core.domain;

import com.turnospro.core.exception.SlotAlreadyReservedException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;


public class ScheduleTest {

    private static final UUID SCHEDULE_ID = UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8");
    private static final ScheduleId scheduleId = new ScheduleId(SCHEDULE_ID);
    private static final TenantId tenantId = new TenantId("clinica-alfa");
    private static final SequenceNumber sequenceNumber = new SequenceNumber(1L);


    @Test
    void shouldReserveSlotSuccessfullyAndDemonstrateImmutability() {
        // Arrange: Prepare identifiers and a map with an AVAILABLE slot
        TimeSlot originalSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );

        Map<TimeSlot, SlotStatus> initialSlots = new HashMap<>();
        initialSlots.put(originalSlot, SlotStatus.AVAILABLE);

        // Instantiate the base schedule
        Schedule initialSchedule = new Schedule(scheduleId, tenantId, sequenceNumber, initialSlots);

        // Act: Execute the reservation (save the returned new schedule)
        Schedule modifiedSchedule = initialSchedule.reserveSlot(originalSlot);

        // Assert 1: Demonstrate Immutability. The original schedule did NOT mutate.
        assertEquals(SlotStatus.AVAILABLE, initialSchedule.timeSlots().get(originalSlot),
                "The original schedule should have remained unaltered.");

        // Assert 2: Verify the new state in the resulting schedule.
        assertNotNull(modifiedSchedule, "The method must return a new Schedule instance.");
        assertEquals(SlotStatus.RESERVED, modifiedSchedule.timeSlots().get(originalSlot),
                "The new schedule must have the slot in RESERVED status.");

        // Assert 3: Verify that the identifiers remained intact
        assertEquals(initialSchedule.scheduleId(), modifiedSchedule.scheduleId());
        assertEquals(initialSchedule.tenantId(), modifiedSchedule.tenantId());
    }

    @Test
    void shouldThrowExceptionWhenSlotIsAlreadyReserved() {
        // Arrange: Create a schedule where the slot is already reserved from the start
        TimeSlot occupiedSlot = new TimeSlot(
                LocalDateTime.of(2026, 6, 20, 9, 0),
                LocalDateTime.of(2026, 6, 20, 9, 30)
        );

        Map<TimeSlot, SlotStatus> slots = new HashMap<>();
        slots.put(occupiedSlot, SlotStatus.RESERVED);

        Schedule schedule = new Schedule(scheduleId, tenantId, sequenceNumber, slots);

        // Act & Assert: Trying to reserve the same slot must fail with your business exception
        assertThrows(SlotAlreadyReservedException.class, () -> {
            schedule.reserveSlot(occupiedSlot);
        }, "The operation should have been rejected by throwing SlotAlreadyReservedException.");
    }
}
