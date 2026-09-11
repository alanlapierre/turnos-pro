package com.turnospro.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnospro.core.application.ScheduleService;
import com.turnospro.core.ports.in.ReserveSlotUseCase;
import com.turnospro.core.ports.out.EventPublisherPort;
import com.turnospro.core.ports.out.ScheduleRepository;
import com.turnospro.infrastructure.adapters.in.observed.ObservedReserveSlotUseCase;
import com.turnospro.infrastructure.adapters.out.messaging.SqsEventPublisherAdapter;
import com.turnospro.infrastructure.adapters.out.persistence.JdbcScheduleRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.sqs.SqsClient;
import javax.sql.DataSource;

@Configuration
public class AppConfig {

    @Bean
    public ScheduleRepository scheduleRepository(DataSource dataSource) {
        return new JdbcScheduleRepository(dataSource);
    }

    @Bean
    public EventPublisherPort eventPublisherPort(
            SqsClient sqsClient,
            @Value("${aws.sqs.queue-url}") String queueUrl,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            @Value("${aws.sqs.publish.max-attempts:3}") int maxAttempts,
            @Value("${aws.sqs.publish.backoff-ms:300}") long backoffMillis,
            @Value("${aws.sqs.publish.fail-open:true}") boolean failOpen) {

        return new SqsEventPublisherAdapter(sqsClient, queueUrl, objectMapper, meterRegistry,
                maxAttempts, backoffMillis, failOpen);
    }

    @Bean
    public ScheduleService scheduleService(
            ScheduleRepository scheduleRepository,
            EventPublisherPort eventPublisherPort) {

        return new ScheduleService(scheduleRepository, eventPublisherPort);
    }

    @Bean
    @Primary
    public ReserveSlotUseCase reserveSlotUseCase(ScheduleService scheduleService,
                                                 ObservationRegistry observationRegistry) {
        // Wrap the core use case with the observability decorator
        return new ObservedReserveSlotUseCase(scheduleService, observationRegistry);
    }
}