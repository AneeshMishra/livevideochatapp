package com.platform.userprofile.repository;

import com.platform.userprofile.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    Optional<UserProfile> findByUsername(String username);

    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE UserProfile p SET p.followingCount = p.followingCount + 1 WHERE p.userId = :userId")
    void incrementFollowingCount(UUID userId);

    @Modifying
    @Query("UPDATE UserProfile p SET p.followingCount = GREATEST(p.followingCount - 1, 0) WHERE p.userId = :userId")
    void decrementFollowingCount(UUID userId);
}
