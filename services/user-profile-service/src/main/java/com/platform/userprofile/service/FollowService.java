package com.platform.userprofile.service;

import com.platform.userprofile.domain.Follow;
import com.platform.userprofile.event.UserProfileEvent;
import com.platform.userprofile.event.UserProfileEventPublisher;
import com.platform.userprofile.exception.AlreadyFollowingException;
import com.platform.userprofile.exception.ProfileNotFoundException;
import com.platform.userprofile.exception.SelfActionException;
import com.platform.userprofile.repository.FollowRepository;
import com.platform.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserProfileRepository profileRepository;
    private final UserProfileEventPublisher eventPublisher;

    @Transactional
    public Follow follow(UUID followerId, UUID followeeId) {
        if (followerId.equals(followeeId)) {
            throw new SelfActionException("follow");
        }
        if (!profileRepository.existsById(followeeId)) {
            throw new ProfileNotFoundException("Broadcaster profile not found: " + followeeId);
        }
        if (followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)) {
            throw new AlreadyFollowingException();
        }

        Follow follow = Follow.of(followerId, followeeId);
        Follow saved = followRepository.save(follow);

        profileRepository.incrementFollowingCount(followerId);

        eventPublisher.publish(new UserProfileEvent.FollowedBroadcaster(
                followerId, followeeId, Instant.now()));
        return saved;
    }

    @Transactional
    public void unfollow(UUID followerId, UUID followeeId) {
        followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId)
                .ifPresent(follow -> {
                    followRepository.delete(follow);
                    profileRepository.decrementFollowingCount(followerId);
                    eventPublisher.publish(new UserProfileEvent.UnfollowedBroadcaster(
                            followerId, followeeId, Instant.now()));
                });
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(UUID followerId, UUID followeeId) {
        return followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    @Transactional(readOnly = true)
    public Page<Follow> listFollowing(UUID followerId, Pageable pageable) {
        return followRepository.findByFollowerIdOrderByCreatedAtDesc(followerId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Follow> listFollowers(UUID followeeId, Pageable pageable) {
        return followRepository.findByFolloweeIdOrderByCreatedAtDesc(followeeId, pageable);
    }

    @Transactional(readOnly = true)
    public long countFollowers(UUID followeeId) {
        return followRepository.countByFolloweeId(followeeId);
    }
}
