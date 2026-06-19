package com.platform.userprofile.service;

import com.platform.userprofile.event.UserProfileEventPublisher;
import com.platform.userprofile.exception.AlreadyFollowingException;
import com.platform.userprofile.exception.SelfActionException;
import com.platform.userprofile.repository.FollowRepository;
import com.platform.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock FollowRepository followRepository;
    @Mock UserProfileRepository profileRepository;
    @Mock UserProfileEventPublisher eventPublisher;

    @InjectMocks FollowService followService;

    @Test
    void follow_self_throws() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> followService.follow(id, id))
                .isInstanceOf(SelfActionException.class);
    }

    @Test
    void follow_alreadyFollowing_throws() {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();

        when(profileRepository.existsById(followeeId)).thenReturn(true);
        when(followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId)).thenReturn(true);

        assertThatThrownBy(() -> followService.follow(followerId, followeeId))
                .isInstanceOf(AlreadyFollowingException.class);
    }

    @Test
    void unfollow_notFollowing_isNoOp() {
        UUID followerId = UUID.randomUUID();
        UUID followeeId = UUID.randomUUID();
        when(followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId))
                .thenReturn(java.util.Optional.empty());

        followService.unfollow(followerId, followeeId);

        verify(followRepository, never()).delete(any());
        verify(eventPublisher, never()).publish(any());
    }
}
