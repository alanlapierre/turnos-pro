package com.turnospro.core.ports.out;

import com.turnospro.core.domain.event.SlotReservedEvent;

public interface EventPublisherPort {
    void publishSlotReserved(SlotReservedEvent event);
}
