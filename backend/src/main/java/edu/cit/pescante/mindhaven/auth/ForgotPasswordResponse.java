package edu.cit.pescante.mindhaven.auth;

public record ForgotPasswordResponse(
        String message,
        long expiresInMinutes,
        String resetToken,
        String resetPath
) {
}
