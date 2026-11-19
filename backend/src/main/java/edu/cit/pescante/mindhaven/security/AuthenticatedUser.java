package edu.cit.pescante.mindhaven.security;

import edu.cit.pescante.mindhaven.user.OwnerType;
import edu.cit.pescante.mindhaven.user.Role;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        UUID accountId,
        OwnerType ownerType,
        Role role,
        String displayName
) {
}
