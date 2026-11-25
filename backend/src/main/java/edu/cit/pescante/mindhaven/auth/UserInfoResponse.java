package edu.cit.pescante.mindhaven.auth;

import edu.cit.pescante.mindhaven.user.OwnerType;
import edu.cit.pescante.mindhaven.user.Role;

import java.time.Instant;
import java.util.UUID;

public record UserInfoResponse(
        UUID id,
        OwnerType ownerType,
        Role role,
        String displayName,
        String avatarCode,
        String email,
        String mobileNumber,
        Boolean onCall,
        String bio,
        Instant createdAt
) {
}
