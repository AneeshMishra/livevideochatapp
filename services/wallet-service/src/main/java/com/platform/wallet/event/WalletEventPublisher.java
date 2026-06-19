package com.platform.wallet.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.wallet-events}")
    private String topic;

    public void publish(WalletEvent event) {
        String key = switch (event) {
            case WalletEvent.WalletCreated e      -> e.userId().toString();
            case WalletEvent.TokensCredited e     -> e.walletId().toString();
            case WalletEvent.TokensDebited e      -> e.walletId().toString();
            case WalletEvent.TransferCompleted e  -> e.fromWalletId().toString();
        };

        kafkaTemplate.send(topic, key, event)
                .whenComplete((r, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish {} key={}: {}", event.eventType(), key, ex.getMessage());
                    } else {
                        log.debug("Published {} offset={}",
                                event.eventType(), r.getRecordMetadata().offset());
                    }
                });
    }
}
