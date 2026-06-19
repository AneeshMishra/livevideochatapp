package com.platform.userprofile.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        @Size(max = 100)
        String displayName,

        @Size(max = 500)
        String bio,

        @Size(max = 512)
        String avatarUrl,

        // BCP 47 language tag
        @Pattern(regexp = "^[a-z]{2}(-[A-Z]{2})?$", message = "Must be a BCP 47 tag e.g. 'en' or 'en-US'")
        String language,

        // ISO 3166-1 alpha-2
        @Pattern(regexp = "^[A-Z]{2}$", message = "Must be ISO 3166-1 alpha-2 e.g. 'US'")
        String country
) {}
