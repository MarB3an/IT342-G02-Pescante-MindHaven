package edu.cit.pescante.mindhaven.dashboard;

import jakarta.validation.constraints.Pattern;

public record UpdateCounselorOnCallRequest(
        boolean onCall,

        @Pattern(
                regexp = "^$|^\\+?[0-9\\-\\s]{7,20}$",
                message = "Mobile number must be a valid phone number (e.g., +639171234567)."
        )
        String mobileNumber
) {
}
