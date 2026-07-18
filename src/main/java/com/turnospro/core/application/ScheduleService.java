package com.turnospro.core.application;

import com.turnospro.core.domain.Schedule;
import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TimeSlot;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.core.ports.out.ScheduleRepository;

public class ScheduleService implements ReserveSlotUseCase {

    private final ScheduleRepository repository;

    public ScheduleService(ScheduleRepository repository) {
        this.repository = repository;
    }

    @Override
    public void reserve(ScheduleId scheduleId, TimeSlot timeSlot) {
        // 1. Fetch the aggregate from infrastructure via a flat data query
        Schedule schedule = repository.findById(scheduleId).orElseThrow();

        // 2. Apply domain invariants over the immutable Java Record inside the Heap memory
        Schedule updatedSchedule = schedule.reserveSlot(timeSlot);

        // 3. Attempt a flat persistence update against the database storage
        repository.update(updatedSchedule);
        // If a concurrent transaction wins the update race on disk, repository.update()
        // will throw a ConcurrentModificationException here, aborting the thread execution.
    }
}



