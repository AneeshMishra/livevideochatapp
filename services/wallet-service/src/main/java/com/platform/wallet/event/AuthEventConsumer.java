package com.platform.wallet.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reacts to auth.events so wallet creation is decoupled from the auth service.
 * On USER_REGISTERED: auto-creates a zero-balance wallet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthEventConsumer {

    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${app.kafka.topics.auth-events}",
            groupId = "wallet-service",
            containerFactory = "stringKafkaListenerContainerFactory"
    )
    public void onAuthEvent(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            if ("USER_REGISTERED".equals(node.path("eventType").asText())) {
                UUID userId = UUID.fromString(node.path("userId").asText());
                walletService.createWallet(userId);
                log.info("Wallet created for user_id={}", userId);
            }
        } catch (Exception ex) {
            log.error("Failed to process auth event: {}", ex.getMessage(), ex);
        }
    }
}
