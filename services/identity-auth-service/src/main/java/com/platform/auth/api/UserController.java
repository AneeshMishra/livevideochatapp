package com.platform.auth.api;

import com.platform.auth.api.dto.UserSummaryResponse;
import com.platform.auth.domain.Role;
import com.platform.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile retrieval and admin role management")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the authenticated user's own profile")
    @GetMapping("/me")
    public UserSummaryResponse getMe(@AuthenticationPrincipal String userId) {
        return UserSummaryResponse.from(userService.getById(UUID.fromString(userId)));
    }

    @Operation(summary = "Get any user by ID (admin only)")
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummaryResponse getById(@PathVariable UUID id) {
        return UserSummaryResponse.from(userService.getById(id));
    }

    @Operation(summary = "Grant a role to a user (admin only)")
    @PostMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummaryResponse grantRole(@PathVariable UUID id, @PathVariable Role role) {
        return UserSummaryResponse.from(userService.grantRole(id, role));
    }

    @Operation(summary = "Revoke a role from a user (admin only)")
    @DeleteMapping("/{id}/roles/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummaryResponse revokeRole(@PathVariable UUID id, @PathVariable Role role) {
        return UserSummaryResponse.from(userService.revokeRole(id, role));
    }
}
