package com.turnospro.infrastructure.adapters.in;

import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TimeSlot;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.infrastructure.adapters.out.resilience.ResilientExecutor;

public class ResilientReserveSlotUseCaseDecorator implements ReserveSlotUseCase {
    private final ReserveSlotUseCase delegate; // The target flat ScheduleService instance
    private final ResilientExecutor resilientExecutor; // The active operational retry engine

    public ResilientReserveSlotUseCaseDecorator(ReserveSlotUseCase delegate, ResilientExecutor resilientExecutor) {
        this.delegate = delegate;
        this.resilientExecutor = resilientExecutor;
    }

    public void reserve(ScheduleId scheduleId, TimeSlot timeSlot) {
        // Delegate retry loop governance and thread execution to the resilient executor
        resilientExecutor.executeWithRetry(() -> {
            // Every time the executor triggers a retry attempt, invoke the flat application service
            delegate.reserve(scheduleId, timeSlot);
            return null;
        });
    }
}
