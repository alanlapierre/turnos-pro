package com.turnospro.infrastructure.security;

import com.turnospro.core.domain.*;
import com.turnospro.infrastructure.BaseIntegrationTest;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MultiTenantSecurityIT extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcScheduleRepository jdbcScheduleRepository;

    @Test
    @DisplayName("Should reject with HTTP 401 Unauthorized when Authorization header is missing")
    void shouldRejectUnauthenticatedRequests() throws Exception {
        UUID scheduleId = UUID.randomUUID();
        String startTimeIso = LocalDateTime.now().plusDays(1).withNano(0).format(DateTimeFormatter.ISO_DATE_TIME);

        mockMvc.perform(post("/api/schedules/" + scheduleId + "/reserve")
                        .param("startTime", startTimeIso))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Should accept reservation request when JWT contains a valid tenant_id claim")
    void shouldAcceptAuthenticatedTenantRequest() throws Exception {
        TenantId tenantAlfa = new TenantId("clinica-alfa");
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withNano(0);
        TimeSlot slot = new TimeSlot(startTime, startTime.plusMinutes(30));

        // 1. Seed test schedule within ephemeral PostgreSQL container bound to Tenant Alfa context[cite: 3, 7]
        ScopedValue.where(TenantContext.TENANT_KEY, tenantAlfa).run(() -> {
            Schedule schedule = new Schedule(
                    scheduleId,
                    tenantAlfa,
                    new SequenceNumber(1L),
                    Map.of(slot, SlotStatus.AVAILABLE)
            );
            jdbcScheduleRepository.save(schedule);
        });

        // 2. Mint cryptographically signed JWT token for Tenant Alfa
        String validJwt = jwtTokenProvider.generateToken("user-alfa", tenantAlfa);

        // 3. Dispatch perimeter HTTP POST request via MockMvc using valid Bearer credentials
        mockMvc.perform(post("/api/schedules/" + scheduleId.id() + "/reserve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + validJwt)
                        .param("startTime", startTime.format(DateTimeFormatter.ISO_DATE_TIME)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: Tenant Beta MUST NOT be able to reserve or access Tenant Alfa schedule (HTTP 404 Not Found)")
    void shouldPreventCrossTenantDataAccess() throws Exception {
        TenantId tenantAlfa = new TenantId("clinica-alfa");
        TenantId tenantBeta = new TenantId("clinica-beta");
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        LocalDateTime startTime = LocalDateTime.now().plusDays(1).withNano(0);
        TimeSlot slot = new TimeSlot(startTime, startTime.plusMinutes(30));

        // 1. Persist schedule aggregate root owned strictly by Tenant Alfa[cite: 1, 3]
        ScopedValue.where(TenantContext.TENANT_KEY, tenantAlfa).run(() -> {
            Schedule schedule = new Schedule(
                    scheduleId,
                    tenantAlfa,
                    new SequenceNumber(1L),
                    Map.of(slot, SlotStatus.AVAILABLE)
            );
            jdbcScheduleRepository.save(schedule);
        });

        // 2. Attempt reservation using a legitimate JWT signed for Tenant Beta
        String betaJwt = jwtTokenProvider.generateToken("user-beta", tenantBeta);

        // 3. Query returns zero rows due to SQL tenant mismatch filter, triggering ScheduleNotFoundException (HTTP 404)[cite: 3]
        mockMvc.perform(post("/api/schedules/" + scheduleId.id() + "/reserve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + betaJwt)
                        .param("startTime", startTime.format(DateTimeFormatter.ISO_DATE_TIME)))
                .andExpect(status().isNotFound());
    }
}