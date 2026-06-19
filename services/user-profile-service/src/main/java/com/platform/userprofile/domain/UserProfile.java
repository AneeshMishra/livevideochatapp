package com.platform.userprofile.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    // Primary key is the userId from identity-auth-service — not generated here
    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(length = 500)
    private String bio;

    // BCP 47 language tag, e.g. "en", "fr"
    @Column(length = 10)
    private String language = "en";

    // ISO 3166-1 alpha-2 country code
    @Column(length = 2)
    private String country;

    // Denormalised — maintained by FollowService to avoid expensive COUNT queries
    @Column(name = "following_count", nullable = false)
    private int followingCount = 0;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static UserProfile bootstrap(UUID userId, String username, String email) {
        UserProfile p = new UserProfile();
        p.setUserId(userId);
        p.setUsername(username);
        p.setDisplayName(username);
        return p;
    }
}
