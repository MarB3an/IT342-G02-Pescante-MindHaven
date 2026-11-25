package edu.cit.pescante.mindhaven.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email address is required.")
        @Email(message = "Please enter a valid email address.")
        @Size(max = 255, message = "Email must not exceed 255 characters.")
        String email
) {
}
