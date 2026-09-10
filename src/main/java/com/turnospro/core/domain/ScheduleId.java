package com.turnospro.core.domain;

import java.util.UUID;

public record ScheduleId(UUID id) {

    public ScheduleId {
        if (id == null) {
            throw new IllegalArgumentException("Schedule ID cannot be null");
        }
    }
}
