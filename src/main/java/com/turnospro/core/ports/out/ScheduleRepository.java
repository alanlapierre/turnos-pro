package com.turnospro.core.ports.out;

import com.turnospro.core.domain.Schedule;
import com.turnospro.core.domain.ScheduleId;
import java.util.Optional;

public interface ScheduleRepository {
    Optional<Schedule> findById(ScheduleId scheduleId);
    void update(Schedule schedule);
    void save(Schedule schedule);
}
