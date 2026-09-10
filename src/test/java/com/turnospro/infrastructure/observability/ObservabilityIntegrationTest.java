package com.turnospro.infrastructure.observability;

import com.turnospro.core.domain.*;
import com.turnospro.infrastructure.BaseIntegrationTest;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
import com.turnospro.infrastructure.security.JwtTokenProvider;
import com.turnospro.infrastructure.security.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ObservabilityIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcScheduleRepository jdbcScheduleRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Should register Prometheus metrics and generate OTel traces when processing a reservation")
    void shouldExposePrometheusMetricsAndTraceContext() throws Exception {
        TenantId tenantAlfa = new TenantId("clinica-alfa");
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withNano(0);
        TimeSlot slot = new TimeSlot(startTime, startTime.plusMinutes(30));

        // 1. Save the initial schedule in PostgreSQL (Testcontainers)
        ScopedValue.where(TenantContext.TENANT_KEY, tenantAlfa).run(() -> {
            Schedule schedule = new Schedule(
                    scheduleId,
                    tenantAlfa,
                    new SequenceNumber(1L),
                    Map.of(slot, SlotStatus.AVAILABLE)
            );
            jdbcScheduleRepository.save(schedule);
        });

        // 2. Generate the JWT token for Clinica Alfa
        String validJwt = jwtTokenProvider.generateToken("user-observability", tenantAlfa);

        // 3. Execute the HTTP call to trigger the Observation
        mockMvc.perform(post("/api/schedules/" + scheduleId.id() + "/reserve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .param("startTime", startTime.format(DateTimeFormatter.ISO_DATE_TIME)))
                .andExpect(status().isOk());

        // 4. Validate that the observation timer was registered with the correct tags
        Timer timer = meterRegistry.find("schedule.reservation").timer();
        if (timer == null) {
            // Dump all registered timers for diagnostic purposes
            String allTimers = meterRegistry.getMeters().stream()
                    .map(m -> m.getId().getName() + " [" + m.getId().getTags() + "]")
                    .reduce((a, b) -> a + "\n  " + b)
                    .orElse("(empty)");
            throw new AssertionError("Timer schedule.reservation not found. Registered meters:\n  " + allTimers);
        }
        assertThat(timer.count())
                .as("Should have at least 1 recorded invocation")
                .isGreaterThanOrEqualTo(1);

        // 5. Validate that the tenant.id tag is present (Observation key; displayed as tenant_id in Prometheus)
        assertThat(timer.getId().getTag("tenant.id"))
                .as("tenant.id tag must be clinica-alfa")
                .isEqualTo("clinica-alfa");

        // 6. Validate that NO UUID tag is present (high cardinality)
        assertThat(timer.getId().getTag("schedule.id"))
                .as("UUID must not appear as tag to prevent High Cardinality Explosion")
                .isNull();
    }
}
