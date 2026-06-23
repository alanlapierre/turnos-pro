package schedule.domain.models;

import java.time.LocalDateTime;

public record TimeSlot(
        LocalDateTime start,
        LocalDateTime end
) {

    public TimeSlot {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end cannot be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("start cannot be after end");
        }
    }
}
