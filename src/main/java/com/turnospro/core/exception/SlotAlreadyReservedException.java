package com.turnospro.core.exception;

public class SlotAlreadyReservedException extends RuntimeException {
    public SlotAlreadyReservedException(String message) {
        super(message);
    }
}
