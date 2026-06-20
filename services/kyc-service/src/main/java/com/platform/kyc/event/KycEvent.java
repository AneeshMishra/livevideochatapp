package com.platform.kyc.event;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "eventType")
public sealed interface KycEvent permits
        KycEvent.KycSubmitted,
        KycEvent.KycApproved,
        KycEvent.KycRejected,
        KycEvent.KycExpired {

    record KycSubmitted(
            UUID applicantId,
            String applicantType,
            String vendorProvider,
            Instant submittedAt
    ) implements KycEvent {}

    record KycApproved(
            UUID applicantId,
            String applicantType,
            Instant verifiedAt,
            Instant expiresAt
    ) implements KycEvent {}

    record KycRejected(
            UUID applicantId,
            String applicantType,
            String reason,
            Instant rejectedAt
    ) implements KycEvent {}

    record KycExpired(
            UUID applicantId,
            String applicantType,
            Instant expiredAt
    ) implements KycEvent {}
}
