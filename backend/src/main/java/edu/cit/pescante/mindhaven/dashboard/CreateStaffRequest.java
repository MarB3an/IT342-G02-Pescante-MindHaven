package edu.cit.pescante.mindhaven.dashboard;

import edu.cit.pescante.mindhaven.user.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateStaffRequest(
        @NotBlank(message = "Full name is required.")
        @Size(min = 2, max = 120, message = "Full name must be between 2 and 120 characters.")
        String fullName,

        @NotBlank(message = "Email address is required.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters.")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, and one number."
        )
        String password,

        @NotNull(message = "Role is required.")
        Role role,

        @Pattern(
                regexp = "^$|^\\+?[0-9\\-\\s]{7,20}$",
                message = "Mobile number must be a valid phone number (e.g., +639171234567)."
        )
        String mobileNumber,

        boolean onCall
) {
}
