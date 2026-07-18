package com.turnospro.core.ports.in;

import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TimeSlot;

public interface ReserveSlotUseCase {
    void reserve(ScheduleId scheduleId, TimeSlot timeSlot);
}
