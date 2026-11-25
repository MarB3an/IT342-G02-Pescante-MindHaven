package edu.cit.pescante.mindhaven.profile;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSelfProfileRequest(
        // Student fields
        @Size(min = 4, max = 30, message = "Pseudonym must be between 4 and 30 characters.")
        String pseudonymHandle,

        @Size(max = 40, message = "Avatar code is too long.")
        String avatarCode,

        // Staff fields (Counselor / Admin)
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters.")
        String fullName,

        @Size(max = 32, message = "Mobile number must not exceed 32 characters.")
        @Pattern(
                regexp = "^$|^[+]?[0-9\\s\\-()]{7,20}$",
                message = "Please enter a valid mobile number (e.g., +639171234567)."
        )
        String mobileNumber,

        Boolean onCall,

        // Shared optional field
        @Size(max = 200, message = "Bio must not exceed 200 characters.")
        String bio
) {
}
