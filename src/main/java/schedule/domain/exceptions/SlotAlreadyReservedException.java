package schedule.domain.exceptions;

public class SlotAlreadyReservedException extends RuntimeException {
    public SlotAlreadyReservedException(String message) {
        super(message);
    }
}
