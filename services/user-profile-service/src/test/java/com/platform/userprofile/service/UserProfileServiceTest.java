package com.platform.userprofile.service;

import com.platform.userprofile.domain.UserProfile;
import com.platform.userprofile.event.UserProfileEventPublisher;
import com.platform.userprofile.exception.ProfileNotFoundException;
import com.platform.userprofile.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock UserProfileRepository profileRepository;
    @Mock UserProfileEventPublisher eventPublisher;

    @InjectMocks UserProfileService profileService;

    @Test
    void initializeProfile_newUser_savesProfile() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        UserProfile saved = UserProfile.bootstrap(userId, "alice", "alice@example.com");
        when(profileRepository.save(any())).thenReturn(saved);

        UserProfile result = profileService.initializeProfile(userId, "alice", "alice@example.com");

        assertThat(result.getUsername()).isEqualTo("alice");
        verify(profileRepository).save(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void initializeProfile_existingUser_isIdempotent() {
        UUID userId = UUID.randomUUID();
        UserProfile existing = UserProfile.bootstrap(userId, "alice", "alice@example.com");
        when(profileRepository.findById(userId)).thenReturn(Optional.of(existing));

        UserProfile result = profileService.initializeProfile(userId, "alice", "alice@example.com");

        assertThat(result).isSameAs(existing);
        verify(profileRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void getByUserId_notFound_throws() {
        UUID userId = UUID.randomUUID();
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.getByUserId(userId))
                .isInstanceOf(ProfileNotFoundException.class);
    }
}
