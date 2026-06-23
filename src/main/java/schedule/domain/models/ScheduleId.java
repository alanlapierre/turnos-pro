package schedule.domain.models;

public record ScheduleId(String id) {

    public ScheduleId {
        if (id == null) {
            throw new IllegalArgumentException("agenda id cannot be null");
        }
    }
}
