package domain;

import schedule.domain.models.TimeSlot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;


public class TimeSlotTest {

    @Test
    void shouldCreateTimeSlotIfParametersAreValid() {
        LocalDateTime start = LocalDateTime.of(2023, 10, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2023, 10, 1, 11, 0);

        TimeSlot timeSlot = new TimeSlot(start, end);

        assertNotNull(timeSlot);
        assertEquals(start, timeSlot.start());
        assertEquals(end, timeSlot.end());
    }

    @Test
    void shouldThrowExceptionWhenStartIsAfterEnd() {
        LocalDateTime start = LocalDateTime.of(2023, 10, 1, 11, 0);
        LocalDateTime end = LocalDateTime.of(2023, 10, 1, 10, 0);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new TimeSlot(start, end));

        assertEquals("start cannot be after end", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenParametersAreNull() {
        LocalDateTime start = LocalDateTime.of(2023, 10, 1, 11, 0);
        LocalDateTime end = null;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> new TimeSlot(start, end));

        assertEquals("start and end cannot be null", exception.getMessage());
    }

    @Test
    void shouldConsiderTwoInstancesEqualWithSameValues() {
        LocalDateTime start = LocalDateTime.of(2023, 10, 1, 10, 0);
        LocalDateTime end = LocalDateTime.of(2023, 10, 1, 11, 0);

        TimeSlot timeSlot1 = new TimeSlot(start, end);
        TimeSlot timeSlot2 = new TimeSlot(start, end);

        assertEquals(timeSlot1, timeSlot2);
        assertEquals(timeSlot1.hashCode(), timeSlot2.hashCode());
    }
}
