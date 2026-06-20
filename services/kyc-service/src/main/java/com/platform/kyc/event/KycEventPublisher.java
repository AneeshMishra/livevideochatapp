package com.platform.kyc.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycEventPublisher {

    private final KafkaTemplate<String, KycEvent> kafkaTemplate;

    @Value("${app.kafka.topics.kyc-events}")
    private String kycEventsTopic;

    public void publish(KycEvent event) {
        String key = extractApplicantId(event).toString();
        kafkaTemplate.send(kycEventsTopic, key, event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish KYC event [{}] for key {}: {}",
                        event.getClass().getSimpleName(), key, ex.getMessage());
                } else {
                    log.info("Published KYC event [{}] for applicant {}",
                        event.getClass().getSimpleName(), key);
                }
            });
    }

    private UUID extractApplicantId(KycEvent event) {
        return switch (event) {
            case KycEvent.KycSubmitted e -> e.applicantId();
            case KycEvent.KycApproved  e -> e.applicantId();
            case KycEvent.KycRejected  e -> e.applicantId();
            case KycEvent.KycExpired   e -> e.applicantId();
        };
    }
}
