package edu.cit.pescante.mindhaven.auth;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserInfoResponse user
) {
}
