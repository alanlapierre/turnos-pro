package com.turnospro.infrastructure.config;

import com.turnospro.core.application.ScheduleService;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.core.ports.out.ScheduleRepository;
import com.turnospro.infrastructure.adapters.in.observed.ObservedReserveSlotUseCase;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
public class AppConfig {

    @Bean
    public ScheduleRepository scheduleRepository(DataSource dataSource) {
        return new JdbcScheduleRepository(dataSource);
    }

    @Bean
    public ScheduleService scheduleService(ScheduleRepository scheduleRepository) {
        return new ScheduleService(scheduleRepository);
    }

    @Bean
    @Primary
    public ReserveSlotUseCase reserveSlotUseCase(ScheduleService scheduleService,
                                                 ObservationRegistry observationRegistry) {
        // Wrap the core use case with the observability decorator
        return new ObservedReserveSlotUseCase(scheduleService, observationRegistry);
    }
}