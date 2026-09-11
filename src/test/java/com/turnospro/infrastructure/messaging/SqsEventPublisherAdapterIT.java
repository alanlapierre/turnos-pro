package com.turnospro.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnospro.core.domain.ScheduleId;
import com.turnospro.core.domain.TenantId;
import com.turnospro.core.domain.TimeSlot;
import com.turnospro.core.domain.event.SlotReservedEvent;
import com.turnospro.core.ports.out.EventPublisherPort;
import com.turnospro.infrastructure.BaseIntegrationTest;
import com.turnospro.infrastructure.adapters.out.messaging.SqsEventPublisherAdapter;
import com.turnospro.infrastructure.adapters.out.messaging.exception.MessagingInfrastructureException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import software.amazon.awssdk.services.sqs.model.SqsException;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class SqsEventPublisherAdapterIT extends BaseIntegrationTest {

    @Autowired
    private EventPublisherPort eventPublisherPort;

    @Autowired
    private SqsClient sqsClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully publish the SlotReservedEvent to the LocalStack SQS queue")
    void shouldPublishSlotReservedEventToSqs() throws Exception {
        // Given
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        TenantId tenantId = new TenantId("clinica-alfa");
        TimeSlot slot = new TimeSlot(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(30));
        SlotReservedEvent event = SlotReservedEvent.from(scheduleId, tenantId, slot);

        // When: Publish the event through the outbound adapter
        eventPublisherPort.publishSlotReserved(event);

        // Then: Read directly from the ephemeral SQS queue to verify reception
        ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(5) // Long-polling to ensure capture
                .build());

        assertThat(response.messages())
                .as("The SQS queue should contain exactly one message")
                .hasSize(1);

        String messageBody = response.messages().get(0).body();
        JsonNode jsonNode = objectMapper.readTree(messageBody);

        // Validate the payload of the domain event
        assertThat(jsonNode.get("scheduleId").get("id").asText()).isEqualTo(scheduleId.id().toString());
        assertThat(jsonNode.get("tenantId").get("id").asText()).isEqualTo("clinica-alfa");
        assertThat(jsonNode.has("eventId")).isTrue();
        assertThat(jsonNode.has("occurredOn")).isTrue();
    }

    @Test
    @DisplayName("Should publish successfully on the first attempt and record a success metric")
    void shouldPublishOnFirstAttempt() {
        // Given: A healthy SQS client
        SqsClient sqsMock = mock(SqsClient.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // When: Publishing through the adapter
        adapter(sqsMock, meterRegistry, 3, true).publishSlotReserved(sampleEvent());

        // Then: A single send is issued and the success metric is incremented
        verify(sqsMock, times(1)).sendMessage(any(SendMessageRequest.class));
        assertThat(meterRegistry.counter("messaging.publish", "status", "success").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should retry on transient failures and eventually publish successfully")
    void shouldRetryTransientFailureAndSucceed() {
        // Given: A client that fails once and then succeeds
        SqsClient sqsMock = mock(SqsClient.class);
        when(sqsMock.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("transient failure").build())
                .thenReturn(SendMessageResponse.builder().build());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // When: Publishing through the adapter
        adapter(sqsMock, meterRegistry, 3, true).publishSlotReserved(sampleEvent());

        // Then: Only the success metric is recorded after the retry
        verify(sqsMock, times(2)).sendMessage(any(SendMessageRequest.class));
        assertThat(meterRegistry.counter("messaging.publish", "status", "success").count()).isEqualTo(1);
        assertThat(meterRegistry.counter("messaging.publish", "status", "failure").count()).isZero();
    }

    @Test
    @DisplayName("Should swallow the failure when fail-open is enabled so the user experience is not broken")
    void shouldFailOpenAfterExhaustingRetries() {
        // Given: An unavailable SQS client that always fails
        SqsClient sqsMock = mock(SqsClient.class);
        when(sqsMock.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("queue unavailable").build());
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        // When: Publishing through the adapter with fail-open enabled
        adapter(sqsMock, meterRegistry, 3, true).publishSlotReserved(sampleEvent());

        // Then: The failure is swallowed and only the failure metric is recorded
        verify(sqsMock, times(3)).sendMessage(any(SendMessageRequest.class));
        assertThat(meterRegistry.counter("messaging.publish", "status", "failure").count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should propagate MessagingInfrastructureException when fail-open is disabled")
    void shouldFailClosedAfterExhaustingRetries() {
        // Given: An unavailable SQS client that always fails
        SqsClient sqsMock = mock(SqsClient.class);
        when(sqsMock.sendMessage(any(SendMessageRequest.class)))
                .thenThrow(SqsException.builder().message("queue unavailable").build());

        // When/Then: Publishing with fail-open disabled must surface the infrastructure exception
        assertThatThrownBy(() -> adapter(sqsMock, new SimpleMeterRegistry(), 3, false)
                .publishSlotReserved(sampleEvent()))
                .isInstanceOf(MessagingInfrastructureException.class);

        verify(sqsMock, times(3)).sendMessage(any(SendMessageRequest.class));
    }

    private SqsEventPublisherAdapter adapter(SqsClient client, SimpleMeterRegistry meterRegistry, int maxAttempts, boolean failOpen) {
        return new SqsEventPublisherAdapter(
                client, "http://localhost:4566/000000000000/test-queue", objectMapper, meterRegistry, maxAttempts, 0, failOpen);
    }

    private SlotReservedEvent sampleEvent() {
        ScheduleId scheduleId = new ScheduleId(UUID.randomUUID());
        TenantId tenantId = new TenantId("clinica-alfa");
        TimeSlot slot = new TimeSlot(LocalDateTime.now().plusDays(1), LocalDateTime.now().plusDays(1).plusMinutes(30));
        return SlotReservedEvent.from(scheduleId, tenantId, slot);
    }
}