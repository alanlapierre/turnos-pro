package com.turnospro.infrastructure.adapters.in.observed;

import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TimeSlot;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.infrastructure.security.TenantContext;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObservedReserveSlotUseCase implements ReserveSlotUseCase {

    private static final Logger log = LoggerFactory.getLogger(ObservedReserveSlotUseCase.class);

    private final ReserveSlotUseCase delegate;
    private final ObservationRegistry observationRegistry;

    public ObservedReserveSlotUseCase(ReserveSlotUseCase delegate, ObservationRegistry observationRegistry) {
        this.delegate = delegate;
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void reserve(ScheduleId scheduleId, TimeSlot timeSlot) {
        String tenantIdValue = TenantContext.getRequiredTenantId().id();

        // Create the Observation linking metrics, traces and logs
        Observation.createNotStarted("schedule.reservation", observationRegistry)
                // Low Cardinality: Goes to Prometheus (Metrics) + Spans (Traces) + Logs
                .lowCardinalityKeyValue("tenant.id", tenantIdValue)
                // High Cardinality: Goes ONLY to Spans (Traces) and Logs (Avoids Prometheus explosion)
                .highCardinalityKeyValue("schedule.id", scheduleId.id().toString())
                .observe(() -> {
                    log.info("Starting observed slot reservation process for slot: {}", timeSlot);
                    try {
                        delegate.reserve(scheduleId, timeSlot);
                        log.info("Slot reservation successfully processed");
                    } catch (Exception ex) {
                        log.warn("Slot reservation failed during execution: {}", ex.getMessage());
                        throw ex;
                    }
                });
    }
}
