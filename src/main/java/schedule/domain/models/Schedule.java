package schedule.domain.models;

import schedule.domain.exceptions.SlotAlreadyReservedException;
import schedule.domain.types.SlotStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record Schedule(ScheduleId scheduleId, TenantId tenantId, Map<TimeSlot, SlotStatus> timeSlots) {

    public Schedule {
        Objects.requireNonNull(timeSlots, "timeSlots cannot be null");
        timeSlots = Map.copyOf(timeSlots);
    }


    public Map<TimeSlot, SlotStatus> getTimeSlots() {
        return timeSlots;
    }

    public Schedule reserveSlot(TimeSlot timeSlot) {

        Objects.requireNonNull(timeSlot, "timeSlot cannot be null");

        if(!timeSlots.containsKey(timeSlot)) {
            throw new IllegalArgumentException("Time slot is not available");
        }

        SlotStatus currentStatus = timeSlots.get(timeSlot);
        if (currentStatus == SlotStatus.RESERVED) {
            throw new SlotAlreadyReservedException("Time slot is already reserved");
        }
        var modifiedTimeSlots = new HashMap<>(timeSlots);
        modifiedTimeSlots.put(timeSlot, SlotStatus.RESERVED);
        return new Schedule(scheduleId, tenantId, modifiedTimeSlots);
    }

}
