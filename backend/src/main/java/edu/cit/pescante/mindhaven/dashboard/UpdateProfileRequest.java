package edu.cit.pescante.mindhaven.dashboard;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 4, max = 30, message = "Pseudonym handle must be between 4 and 30 characters.")
        String pseudonymHandle,

        @Size(max = 40, message = "Invalid avatar code.")
        String avatarCode
) {
}
