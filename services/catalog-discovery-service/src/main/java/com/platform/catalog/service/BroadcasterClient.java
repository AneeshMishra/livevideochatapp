package com.platform.catalog.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

@Component
public class BroadcasterClient {

    private static final Logger log = LoggerFactory.getLogger(BroadcasterClient.class);

    private final RestTemplate http;
    private final String broadcasterServiceUrl;

    public BroadcasterClient(
            RestTemplateBuilder builder,
            @Value("${broadcaster.service-url}") String broadcasterServiceUrl,
            @Value("${broadcaster.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${broadcaster.read-timeout-ms:3000}") int readTimeoutMs) {

        this.http = builder
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .readTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
        this.broadcasterServiceUrl = broadcasterServiceUrl;
    }

    public Optional<BroadcasterProfile> fetchProfile(String broadcasterId) {
        try {
            String url = broadcasterServiceUrl + "/api/v1/broadcasters/" + broadcasterId;
            BroadcasterProfile profile = http.getForObject(url, BroadcasterProfile.class);
            return Optional.ofNullable(profile);
        } catch (RestClientException e) {
            log.warn("Could not fetch broadcaster profile for {}: {}", broadcasterId, e.getMessage());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BroadcasterProfile {

        @JsonProperty("id")
        private String id;

        @JsonProperty("username")
        private String username;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("avatarUrl")
        private String avatarUrl;

        public String getId() { return id; }
        public String getUsername() { return username; }
        public String getDisplayName() { return displayName; }
        public String getAvatarUrl() { return avatarUrl; }
    }
}
