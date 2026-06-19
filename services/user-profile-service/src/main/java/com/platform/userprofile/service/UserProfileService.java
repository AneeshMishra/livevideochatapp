package com.platform.userprofile.service;

import com.platform.userprofile.domain.UserProfile;
import com.platform.userprofile.event.UserProfileEvent;
import com.platform.userprofile.event.UserProfileEventPublisher;
import com.platform.userprofile.exception.ProfileNotFoundException;
import com.platform.userprofile.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserProfileEventPublisher eventPublisher;

    /**
     * Called by AuthEventConsumer when a USER_REGISTERED event arrives.
     * Idempotent — if the profile already exists (e.g. replay), it's a no-op.
     */
    @Transactional
    public UserProfile initializeProfile(UUID userId, String username, String email) {
        return profileRepository.findById(userId).orElseGet(() -> {
            UserProfile profile = UserProfile.bootstrap(userId, username, email);
            UserProfile saved = profileRepository.save(profile);
            eventPublisher.publish(new UserProfileEvent.ProfileCreated(
                    saved.getUserId(), saved.getUsername(), Instant.now()));
            return saved;
        });
    }

    @Transactional(readOnly = true)
    public UserProfile getByUserId(UUID userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found for user: " + userId));
    }

    @Transactional(readOnly = true)
    public UserProfile getByUsername(String username) {
        return profileRepository.findByUsername(username)
                .orElseThrow(() -> new ProfileNotFoundException("Profile not found: " + username));
    }

    @Transactional
    public UserProfile updateProfile(UUID userId, String displayName, String bio,
                                     String avatarUrl, String language, String country) {
        UserProfile profile = getByUserId(userId);

        if (displayName != null) profile.setDisplayName(displayName.strip());
        if (bio != null)         profile.setBio(bio.strip());
        if (avatarUrl != null)   profile.setAvatarUrl(avatarUrl.strip());
        if (language != null)    profile.setLanguage(language.strip());
        if (country != null)     profile.setCountry(country.toUpperCase().strip());

        UserProfile saved = profileRepository.save(profile);
        eventPublisher.publish(new UserProfileEvent.ProfileUpdated(
                saved.getUserId(), saved.getUsername(), Instant.now()));
        return saved;
    }
}
