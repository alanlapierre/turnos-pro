package com.turnospro.infrastructure.adapters.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.turnospro.core.domain.event.SlotReservedEvent;
import com.turnospro.core.ports.out.EventPublisherPort;
import com.turnospro.infrastructure.adapters.out.messaging.exception.MessagingInfrastructureException;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public class SqsEventPublisherAdapter implements EventPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(SqsEventPublisherAdapter.class);

    private static final String MESSAGING_PUBLISH_METRIC = "messaging.publish";

    private final SqsClient sqsClient;
    private final String queueUrl;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long backoffMillis;
    private final boolean failOpen;

    public SqsEventPublisherAdapter(SqsClient sqsClient, String queueUrl, ObjectMapper objectMapper,
                                    MeterRegistry meterRegistry, int maxAttempts, long backoffMillis, boolean failOpen) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
        this.failOpen = failOpen;
    }

    @Override
    public void publishSlotReserved(SlotReservedEvent event) {
        try {
            // 1. Transform the domain event to JSON
            String payload = objectMapper.writeValueAsString(event);

            // 2. Build the native AWS SDK v2 request
            SendMessageRequest sendMsgRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(payload)
                    .build();

            // 3. Dispatch to SQS (LocalStack) with a short retry loop for transient failures
            Exception lastFailure = null;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                try {
                    sqsClient.sendMessage(sendMsgRequest);
                    meterRegistry.counter(MESSAGING_PUBLISH_METRIC, "status", "success").increment();
                    return;
                } catch (Exception ex) {
                    lastFailure = ex;
                    log.warn("SQS publish attempt {}/{} failed for event {}: {}",
                            attempt, maxAttempts, event.eventId(), ex.getMessage());
                    if (attempt < maxAttempts) {
                        Thread.sleep(backoffMillis);
                    }
                }
            }

            // 4. Failure policy: the reservation is already committed, so a failed notification
            //    must not break the user experience.
            if (failOpen) {
                meterRegistry.counter(MESSAGING_PUBLISH_METRIC, "status", "failure").increment();
                log.error("Failed to publish SlotReservedEvent {} to SQS after {} attempts. Event dropped (fail-open).",
                        event.eventId(), maxAttempts, lastFailure);
            } else {
                throw new MessagingInfrastructureException("Failed to publish SlotReservedEvent to SQS", lastFailure);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            meterRegistry.counter(MESSAGING_PUBLISH_METRIC, "status", "failure").increment();
            log.error("SQS publish for event {} was interrupted.", event.eventId(), ie);
        } catch (MessagingInfrastructureException mie) {
            throw mie;
        } catch (Exception ex) {
            // Serialization or any other unexpected error before/after the retry loop
            if (failOpen) {
                meterRegistry.counter(MESSAGING_PUBLISH_METRIC, "status", "failure").increment();
                log.error("Failed to publish SlotReservedEvent {} to SQS. Event dropped (fail-open).", event.eventId(), ex);
            } else {
                throw new MessagingInfrastructureException("Failed to publish SlotReservedEvent to SQS", ex);
            }
        }
    }
}