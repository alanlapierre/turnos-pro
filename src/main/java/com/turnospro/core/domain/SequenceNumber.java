package com.turnospro.core.domain;

public record SequenceNumber(Long sequenceNumber) {

    public SequenceNumber {
        if (sequenceNumber == null) {
            throw new IllegalArgumentException("Sequence Number cannot be null");
        }
    }
}
