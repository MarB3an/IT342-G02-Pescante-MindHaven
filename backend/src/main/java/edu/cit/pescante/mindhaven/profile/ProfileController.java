package edu.cit.pescante.mindhaven.profile;

import edu.cit.pescante.mindhaven.auth.UserInfoResponse;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ResponseEntity<UserInfoResponse> getMyProfile(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(profileService.getOwnProfile(currentUser));
    }

    @PatchMapping
    public ResponseEntity<UserInfoResponse> updateMyProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateSelfProfileRequest request
    ) {
        return ResponseEntity.ok(profileService.updateOwnProfile(currentUser, request));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserInfoResponse> getProfileById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID userId
    ) {
        profileService.assertSelfOwnership(currentUser, userId);
        return ResponseEntity.ok(profileService.getOwnProfile(currentUser));
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserInfoResponse> updateProfileById(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateSelfProfileRequest request
    ) {
        profileService.assertSelfOwnership(currentUser, userId);
        return ResponseEntity.ok(profileService.updateOwnProfile(currentUser, request));
    }
}
