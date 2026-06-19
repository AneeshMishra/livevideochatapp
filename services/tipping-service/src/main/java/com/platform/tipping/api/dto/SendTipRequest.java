package com.platform.tipping.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendTipRequest(

        @NotNull(message = "recipientId is required")
        UUID recipientId,

        @NotNull(message = "roomId is required")
        UUID roomId,

        @Min(value = 1, message = "tokenAmount must be at least 1")
        long tokenAmount,

        @Size(max = 500, message = "message must be 500 characters or fewer")
        String message,

        // If provided the service validates that tokenAmount matches the item's price
        UUID tipMenuItemId,

        @NotBlank(message = "idempotencyKey is required")
        @Size(max = 255)
        String idempotencyKey,

        // Display name snapshot — avoids a profile lookup at fan-out time
        @Size(max = 100)
        String senderDisplayName
) {}
