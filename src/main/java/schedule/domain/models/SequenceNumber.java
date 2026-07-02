package schedule.domain.models;

public record SequenceNumber(Long sequenceNumber) {

    public SequenceNumber {
        if (sequenceNumber == null) {
            throw new IllegalArgumentException("Sequence Number cannot be null");
        }
    }
}
