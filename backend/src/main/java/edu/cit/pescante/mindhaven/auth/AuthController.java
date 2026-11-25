package edu.cit.pescante.mindhaven.auth;

import edu.cit.pescante.mindhaven.config.AppProperties;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public static final String REFRESH_COOKIE_NAME = "mindhaven_refresh_token";

    private final AuthService authService;
    private final AppProperties appProperties;

    public AuthController(AuthService authService, AppProperties appProperties) {
        this.authService = authService;
        this.appProperties = appProperties;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthSessionResult session = authService.registerStudent(request);
        addRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.status(HttpStatus.CREATED).body(session.response());
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthSessionResult session = authService.login(request);
        addRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String rawRefreshToken = extractRefreshCookie(request);
        AuthService.AuthSessionResult session = authService.refresh(rawRefreshToken);
        addRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.ok(session.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal AuthenticatedUser currentUser
    ) {
        String rawRefreshToken = extractRefreshCookie(request);
        authService.logout(rawRefreshToken, currentUser);
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        return ResponseEntity.ok(authService.getCurrentUser(currentUser));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(authService.requestPasswordReset(request));
    }

    @GetMapping("/reset-password/validate")
    public ResponseEntity<Map<String, Object>> validateResetToken(@RequestParam String token) {
        return ResponseEntity.ok(authService.validatePasswordResetToken(token));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of(
                "message", "Your password has been reset successfully. You can now sign in with your new password."
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<AuthResponse> changePassword(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletResponse response
    ) {
        AuthService.AuthSessionResult session = authService.changePassword(currentUser, request);
        addRefreshCookie(response, session.rawRefreshToken());
        return ResponseEntity.ok(session.response());
    }

    private void addRefreshCookie(HttpServletResponse response, String tokenValue) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, tokenValue)
                .httpOnly(true)
                .secure(appProperties.getSecurity().isCookieSecure())
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(appProperties.getSecurity().getRefreshTokenTtlDays()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(appProperties.getSecurity().isCookieSecure())
                .path("/api/v1/auth")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (REFRESH_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
