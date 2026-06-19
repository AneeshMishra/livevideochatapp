package com.platform.userprofile.api;

import com.platform.userprofile.api.dto.UpdateProfileRequest;
import com.platform.userprofile.api.dto.UserProfileResponse;
import com.platform.userprofile.service.UserProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profiles")
@RequiredArgsConstructor
@Tag(name = "Profiles", description = "Viewer profile management")
@SecurityRequirement(name = "bearerAuth")
public class ProfileController {

    private final UserProfileService profileService;

    @Operation(summary = "Get own profile")
    @GetMapping("/me")
    public UserProfileResponse getMe(@AuthenticationPrincipal String userId) {
        return UserProfileResponse.from(profileService.getByUserId(UUID.fromString(userId)));
    }

    @Operation(summary = "Update own profile")
    @PutMapping("/me")
    public UserProfileResponse updateMe(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody UpdateProfileRequest req
    ) {
        return UserProfileResponse.from(profileService.updateProfile(
                UUID.fromString(userId),
                req.displayName(), req.bio(), req.avatarUrl(), req.language(), req.country()
        ));
    }

    @Operation(summary = "Get any user's public profile by userId")
    @GetMapping("/{userId}")
    public UserProfileResponse getById(@PathVariable UUID userId) {
        return UserProfileResponse.from(profileService.getByUserId(userId));
    }

    @Operation(summary = "Get any user's public profile by username")
    @GetMapping("/by-username/{username}")
    public UserProfileResponse getByUsername(@PathVariable String username) {
        return UserProfileResponse.from(profileService.getByUsername(username));
    }
}
