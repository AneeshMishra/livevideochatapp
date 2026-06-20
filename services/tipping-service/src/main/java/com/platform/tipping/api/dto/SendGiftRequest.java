package com.platform.tipping.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record SendGiftRequest(
        @NotNull UUID giftTypeId,
        @NotNull UUID recipientId,
        @NotNull UUID roomId,
        @Size(max = 200) String message,
        @NotBlank String idempotencyKey,
        @NotBlank String senderDisplayName
) {}
