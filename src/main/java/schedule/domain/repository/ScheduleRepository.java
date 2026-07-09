package schedule.domain.repository;

import schedule.domain.models.Schedule;

import java.util.Optional;
import java.util.UUID;

public interface ScheduleRepository {
    Optional<Schedule> findById(UUID id);
    void update(Schedule schedule);
}
