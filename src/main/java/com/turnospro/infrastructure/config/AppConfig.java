package com.turnospro.infrastructure.config;

import com.turnospro.core.application.ScheduleService;
import com.turnospro.core.ports.out.ScheduleRepository;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}