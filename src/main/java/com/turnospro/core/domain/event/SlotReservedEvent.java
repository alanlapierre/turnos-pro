package com.turnospro.core.domain.event;

import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TenantId;
import com.turnospro.core.domain.TimeSlot;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SlotReservedEvent(
        UUID eventId,
        ScheduleId scheduleId,
        TenantId tenantId,
        TimeSlot slot,
        Instant occurredOn
) {
    public SlotReservedEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(scheduleId, "scheduleId must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(slot, "slot must not be null");
        Objects.requireNonNull(occurredOn, "occurredOn must not be null");
    }

    // Convenience factory method to build the event when a reservation is confirmed
    public static SlotReservedEvent from(ScheduleId scheduleId, TenantId tenantId, TimeSlot slot) {
        return new SlotReservedEvent(
                UUID.randomUUID(),
                scheduleId,
                tenantId,
                slot,
                Instant.now()
        );
    }
}
