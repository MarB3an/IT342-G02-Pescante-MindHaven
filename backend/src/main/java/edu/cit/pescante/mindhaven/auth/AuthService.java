package edu.cit.pescante.mindhaven.auth;

import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.config.AppProperties;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.security.EmailHashService;
import edu.cit.pescante.mindhaven.security.JwtService;
import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.OwnerType;
import edu.cit.pescante.mindhaven.user.PasswordResetToken;
import edu.cit.pescante.mindhaven.user.PasswordResetTokenRepository;
import edu.cit.pescante.mindhaven.user.PseudonymGenerator;
import edu.cit.pescante.mindhaven.user.RefreshToken;
import edu.cit.pescante.mindhaven.user.RefreshTokenRepository;
import edu.cit.pescante.mindhaven.user.Role;
import edu.cit.pescante.mindhaven.user.StaffUser;
import edu.cit.pescante.mindhaven.user.StaffUserRepository;
import edu.cit.pescante.mindhaven.user.StudentAccount;
import edu.cit.pescante.mindhaven.user.StudentAccountRepository;
import edu.cit.pescante.mindhaven.user.StudentProfile;
import edu.cit.pescante.mindhaven.user.StudentProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public record AuthSessionResult(AuthResponse response, String rawRefreshToken) {
    }

    private final StudentAccountRepository studentAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StaffUserRepository staffUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailHashService emailHashService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final PseudonymGenerator pseudonymGenerator;
    private final AuditLogService auditLogService;
    private final AppProperties appProperties;

    public AuthService(
            StudentAccountRepository studentAccountRepository,
            StudentProfileRepository studentProfileRepository,
            StaffUserRepository staffUserRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailHashService emailHashService,
            JwtService jwtService,
            PasswordEncoder passwordEncoder,
            PseudonymGenerator pseudonymGenerator,
            AuditLogService auditLogService,
            AppProperties appProperties
    ) {
        this.studentAccountRepository = studentAccountRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.staffUserRepository = staffUserRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailHashService = emailHashService;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.pseudonymGenerator = pseudonymGenerator;
        this.auditLogService = auditLogService;
        this.appProperties = appProperties;
    }

    @Transactional
    public AuthSessionResult registerStudent(RegisterRequest request) {
        if (!request.password().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password and confirm password do not match.");
        }

        String normalizedEmail = emailHashService.normalizeEmail(request.email());
        validateCampusDomain(normalizedEmail);

        String emailHash = emailHashService.hmacEmail(normalizedEmail);
        if (studentAccountRepository.existsByEmailHash(emailHash)
                || staffUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this campus email already exists.");
        }

        StudentAccount account = new StudentAccount();
        account.setEmailHash(emailHash);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        account.setActive(true);
        account = studentAccountRepository.save(account);

        String avatarCode = request.preferredAvatar() != null
                && PseudonymGenerator.VALID_AVATARS.contains(request.preferredAvatar().trim().toLowerCase())
                ? request.preferredAvatar().trim().toLowerCase()
                : pseudonymGenerator.pickDefaultAvatar();

        StudentProfile profile = new StudentProfile();
        profile.setAccount(account);
        profile.setPseudonymHandle(pseudonymGenerator.generateUniqueHandle());
        profile.setAvatarCode(avatarCode);
        profile.setActive(true);
        profile = studentProfileRepository.save(profile);

        auditLogService.record(
                Role.ROLE_STUDENT.name(),
                profile.getPseudonymHandle(),
                "STUDENT_REGISTERED",
                "Pseudonymous student account created (email stored as HMAC-SHA256 hash only)."
        );

        return issueStudentSession(account, profile);
    }

    @Transactional
    public AuthSessionResult login(LoginRequest request) {
        String normalizedEmail = emailHashService.normalizeEmail(request.email());

        Optional<StaffUser> staffOpt = staffUserRepository.findByEmailIgnoreCase(normalizedEmail);
        if (staffOpt.isPresent()) {
            return loginStaff(staffOpt.get(), request.password());
        }

        String emailHash = emailHashService.hmacEmail(normalizedEmail);
        Optional<StudentAccount> studentOpt = studentAccountRepository.findByEmailHash(emailHash);
        if (studentOpt.isPresent()) {
            return loginStudent(studentOpt.get(), request.password());
        }

        throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    private AuthSessionResult loginStaff(StaffUser staff, String rawPassword) {
        if (!staff.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This staff account has been deactivated. Contact a system administrator.");
        }
        if (staff.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked due to repeated failed login attempts. Try again later.");
        }
        if (!passwordEncoder.matches(rawPassword, staff.getPasswordHash())) {
            int attempts = staff.getFailedAttempts() + 1;
            staff.setFailedAttempts(attempts);
            if (attempts >= appProperties.getSecurity().getMaxFailedAttempts()) {
                staff.setLockedUntil(Instant.now().plus(appProperties.getSecurity().getLockoutMinutes(), ChronoUnit.MINUTES));
            }
            staffUserRepository.save(staff);
            auditLogService.record(staff.getRole().name(), staff.getEmail(), "LOGIN_FAILED", "Invalid password attempt (" + attempts + ").");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        staff.setFailedAttempts(0);
        staff.setLockedUntil(null);
        staffUserRepository.save(staff);

        auditLogService.record(staff.getRole().name(), staff.getFullName(), "LOGIN_SUCCESS", "Authenticated staff session started.");
        return issueStaffSession(staff);
    }

    private AuthSessionResult loginStudent(StudentAccount account, String rawPassword) {
        if (!account.isActive()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This student account is inactive.");
        }
        if (account.isLocked()) {
            throw new ApiException(HttpStatus.LOCKED, "Account temporarily locked due to repeated failed login attempts. Try again later.");
        }
        if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
            int attempts = account.getFailedAttempts() + 1;
            account.setFailedAttempts(attempts);
            if (attempts >= appProperties.getSecurity().getMaxFailedAttempts()) {
                account.setLockedUntil(Instant.now().plus(appProperties.getSecurity().getLockoutMinutes(), ChronoUnit.MINUTES));
            }
            studentAccountRepository.save(account);
            auditLogService.record(Role.ROLE_STUDENT.name(), "AnonymousStudent", "LOGIN_FAILED", "Invalid password attempt (" + attempts + ").");
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
        }

        account.setFailedAttempts(0);
        account.setLockedUntil(null);
        studentAccountRepository.save(account);

        StudentProfile profile = studentProfileRepository.findByAccountAccountId(account.getAccountId())
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Student profile record is missing."));
        profile.setLastSeenAt(Instant.now());
        studentProfileRepository.save(profile);

        auditLogService.record(Role.ROLE_STUDENT.name(), profile.getPseudonymHandle(), "LOGIN_SUCCESS", "Pseudonymous student session started.");
        return issueStudentSession(account, profile);
    }

    @Transactional
    public AuthSessionResult refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again.");
        }

        String tokenHash = emailHashService.sha256Hex(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or revoked session token."));

        if (stored.isRevoked() || stored.isExpired()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Session has expired or been logged out.");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        if (stored.getOwnerType() == OwnerType.STUDENT) {
            StudentAccount account = studentAccountRepository.findById(stored.getOwnerId())
                    .filter(StudentAccount::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Student account is no longer active."));
            StudentProfile profile = studentProfileRepository.findByAccountAccountId(account.getAccountId())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Student profile not found."));
            return issueStudentSession(account, profile);
        } else {
            StaffUser staff = staffUserRepository.findById(stored.getOwnerId())
                    .filter(StaffUser::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Staff account is no longer active."));
            return issueStaffSession(staff);
        }
    }

    @Transactional
    public void logout(String rawRefreshToken, AuthenticatedUser currentUser) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            String tokenHash = emailHashService.sha256Hex(rawRefreshToken);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
                token.setRevoked(true);
                refreshTokenRepository.save(token);
            });
        }
        if (currentUser != null) {
            refreshTokenRepository.revokeAllActiveByOwner(currentUser.ownerType(), currentUser.accountId());
            auditLogService.record(currentUser.role().name(), currentUser.displayName(), "LOGOUT", "Authenticated session terminated and refresh tokens revoked.");
        }
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(AuthenticatedUser currentUser) {
        if (currentUser.ownerType() == OwnerType.STUDENT) {
            StudentProfile profile = studentProfileRepository.findById(currentUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Student session is no longer valid."));
            return toStudentUserInfo(profile);
        } else {
            StaffUser staff = staffUserRepository.findById(currentUser.id())
                    .filter(StaffUser::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Staff session is no longer valid."));
            return toStaffUserInfo(staff);
        }
    }

    private AuthSessionResult issueStudentSession(StudentAccount account, StudentProfile profile) {
        AuthenticatedUser principal = new AuthenticatedUser(
                profile.getPseudonymId(),
                account.getAccountId(),
                OwnerType.STUDENT,
                Role.ROLE_STUDENT,
                profile.getPseudonymHandle()
        );
        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = createAndStoreRefreshToken(OwnerType.STUDENT, account.getAccountId());

        return new AuthSessionResult(
                new AuthResponse(accessToken, jwtService.getAccessTokenTtlSeconds(), toStudentUserInfo(profile)),
                rawRefreshToken
        );
    }

    private AuthSessionResult issueStaffSession(StaffUser staff) {
        AuthenticatedUser principal = new AuthenticatedUser(
                staff.getStaffId(),
                staff.getStaffId(),
                OwnerType.STAFF,
                staff.getRole(),
                staff.getFullName()
        );
        String accessToken = jwtService.generateAccessToken(principal);
        String rawRefreshToken = createAndStoreRefreshToken(OwnerType.STAFF, staff.getStaffId());

        return new AuthSessionResult(
                new AuthResponse(accessToken, jwtService.getAccessTokenTtlSeconds(), toStaffUserInfo(staff)),
                rawRefreshToken
        );
    }

    private String createAndStoreRefreshToken(OwnerType ownerType, java.util.UUID ownerId) {
        String rawToken = emailHashService.generateOpaqueToken();
        RefreshToken entity = new RefreshToken();
        entity.setTokenHash(emailHashService.sha256Hex(rawToken));
        entity.setOwnerType(ownerType);
        entity.setOwnerId(ownerId);
        entity.setExpiresAt(Instant.now().plus(appProperties.getSecurity().getRefreshTokenTtlDays(), ChronoUnit.DAYS));
        entity.setRevoked(false);
        refreshTokenRepository.save(entity);
        return rawToken;
    }

    private void validateCampusDomain(String normalizedEmail) {
        int atIndex = normalizedEmail.lastIndexOf('@');
        if (atIndex < 1 || atIndex == normalizedEmail.length() - 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Please enter a valid campus email address.");
        }
        String domain = normalizedEmail.substring(atIndex + 1);
        List<String> allowed = appProperties.getSecurity().getAllowedEmailDomainList();
        if (allowed.isEmpty()) {
            return;
        }
        boolean matched = allowed.stream().anyMatch(suffix ->
                domain.equals(suffix) || domain.endsWith("." + suffix)
        );
        if (!matched) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Please register using an authorized university email address (e.g., @cit.edu or *.edu)."
            );
        }
    }

    @Transactional
    public ForgotPasswordResponse requestPasswordReset(ForgotPasswordRequest request) {
        String normalizedEmail = emailHashService.normalizeEmail(request.email());
        long ttlMinutes = appProperties.getSecurity().getPasswordResetTtlMinutes();
        String genericMessage = "If an active account exists for that email, a secure password reset link has been generated (expires in " + ttlMinutes + " minutes).";

        Optional<StaffUser> staffOpt = staffUserRepository.findByEmailIgnoreCase(normalizedEmail)
                .filter(StaffUser::isActive);
        if (staffOpt.isPresent()) {
            StaffUser staff = staffOpt.get();
            String rawToken = createAndStorePasswordResetToken(OwnerType.STAFF, staff.getStaffId(), ttlMinutes);
            auditLogService.record(staff.getRole().name(), staff.getFullName(), "PASSWORD_RESET_REQUESTED", "Single-use password reset token generated.");
            log.info("Password reset token generated for staff {} -> /reset-password?token={}", staff.getEmail(), rawToken);
            return buildForgotPasswordResponse(genericMessage, ttlMinutes, rawToken);
        }

        String emailHash = emailHashService.hmacEmail(normalizedEmail);
        Optional<StudentAccount> studentOpt = studentAccountRepository.findByEmailHash(emailHash)
                .filter(StudentAccount::isActive);
        if (studentOpt.isPresent()) {
            StudentAccount account = studentOpt.get();
            String rawToken = createAndStorePasswordResetToken(OwnerType.STUDENT, account.getAccountId(), ttlMinutes);
            String handle = studentProfileRepository.findByAccountAccountId(account.getAccountId())
                    .map(StudentProfile::getPseudonymHandle)
                    .orElse("AnonymousStudent");
            auditLogService.record(Role.ROLE_STUDENT.name(), handle, "PASSWORD_RESET_REQUESTED", "Single-use password reset token generated.");
            log.info("Password reset token generated for student {} -> /reset-password?token={}", handle, rawToken);
            return buildForgotPasswordResponse(genericMessage, ttlMinutes, rawToken);
        }

        return new ForgotPasswordResponse(genericMessage, ttlMinutes, null, null);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> validatePasswordResetToken(String rawToken) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (rawToken == null || rawToken.isBlank()) {
            result.put("valid", false);
            result.put("message", "Reset token is missing.");
            return result;
        }
        String tokenHash = emailHashService.sha256Hex(rawToken.trim());
        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByTokenHash(tokenHash);
        if (tokenOpt.isEmpty()) {
            result.put("valid", false);
            result.put("message", "Invalid password reset link or token.");
            return result;
        }
        PasswordResetToken token = tokenOpt.get();
        if (token.isUsed()) {
            result.put("valid", false);
            result.put("message", "This password reset token has already been used.");
            return result;
        }
        if (token.isExpired()) {
            result.put("valid", false);
            result.put("message", "This password reset token has expired. Please request a new one.");
            return result;
        }
        result.put("valid", true);
        result.put("expiresAt", token.getExpiresAt());
        result.put("message", "Token is valid.");
        return result;
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match.");
        }

        String tokenHash = emailHashService.sha256Hex(request.token().trim());
        PasswordResetToken stored = passwordResetTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid or expired password reset token."));

        if (stored.isUsed()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This password reset token has already been used.");
        }
        if (stored.isExpired()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "This password reset token has expired. Please request a new one.");
        }

        String encodedPassword = passwordEncoder.encode(request.newPassword());

        if (stored.getOwnerType() == OwnerType.STUDENT) {
            StudentAccount account = studentAccountRepository.findById(stored.getOwnerId())
                    .filter(StudentAccount::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Associated student account is no longer active."));
            account.setPasswordHash(encodedPassword);
            account.setFailedAttempts(0);
            account.setLockedUntil(null);
            studentAccountRepository.save(account);

            refreshTokenRepository.revokeAllActiveByOwner(OwnerType.STUDENT, account.getAccountId());

            String handle = studentProfileRepository.findByAccountAccountId(account.getAccountId())
                    .map(StudentProfile::getPseudonymHandle)
                    .orElse("AnonymousStudent");
            auditLogService.record(Role.ROLE_STUDENT.name(), handle, "PASSWORD_RESET_COMPLETED", "Student password reset using single-use token; active sessions revoked.");
        } else {
            StaffUser staff = staffUserRepository.findById(stored.getOwnerId())
                    .filter(StaffUser::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Associated staff account is no longer active."));
            staff.setPasswordHash(encodedPassword);
            staff.setFailedAttempts(0);
            staff.setLockedUntil(null);
            staffUserRepository.save(staff);

            refreshTokenRepository.revokeAllActiveByOwner(OwnerType.STAFF, staff.getStaffId());
            auditLogService.record(staff.getRole().name(), staff.getFullName(), "PASSWORD_RESET_COMPLETED", "Staff password reset using single-use token; active sessions revoked.");
        }

        stored.setUsed(true);
        stored.setUsedAt(Instant.now());
        passwordResetTokenRepository.save(stored);
    }

    @Transactional
    public AuthSessionResult changePassword(AuthenticatedUser currentUser, ChangePasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password and confirm password do not match.");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different from your current password.");
        }

        if (currentUser.ownerType() == OwnerType.STUDENT) {
            StudentAccount account = studentAccountRepository.findById(currentUser.accountId())
                    .filter(StudentAccount::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Student account is no longer active."));

            if (!passwordEncoder.matches(request.currentPassword(), account.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
            }

            account.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            account.setFailedAttempts(0);
            account.setLockedUntil(null);
            studentAccountRepository.save(account);

            refreshTokenRepository.revokeAllActiveByOwner(OwnerType.STUDENT, account.getAccountId());

            StudentProfile profile = studentProfileRepository.findById(currentUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));
            auditLogService.record(Role.ROLE_STUDENT.name(), profile.getPseudonymHandle(), "PASSWORD_CHANGED", "Student changed password; previous sessions revoked.");
            return issueStudentSession(account, profile);
        } else {
            StaffUser staff = staffUserRepository.findById(currentUser.id())
                    .filter(StaffUser::isActive)
                    .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Staff account is no longer active."));

            if (!passwordEncoder.matches(request.currentPassword(), staff.getPasswordHash())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
            }

            staff.setPasswordHash(passwordEncoder.encode(request.newPassword()));
            staff.setFailedAttempts(0);
            staff.setLockedUntil(null);
            staffUserRepository.save(staff);

            refreshTokenRepository.revokeAllActiveByOwner(OwnerType.STAFF, staff.getStaffId());
            auditLogService.record(staff.getRole().name(), staff.getFullName(), "PASSWORD_CHANGED", "Staff user changed password; previous sessions revoked.");
            return issueStaffSession(staff);
        }
    }

    private String createAndStorePasswordResetToken(OwnerType ownerType, UUID ownerId, long ttlMinutes) {
        Instant now = Instant.now();
        passwordResetTokenRepository.invalidateAllActiveByOwner(ownerType, ownerId, now);

        String rawToken = emailHashService.generateOpaqueToken();
        PasswordResetToken entity = new PasswordResetToken();
        entity.setTokenHash(emailHashService.sha256Hex(rawToken));
        entity.setOwnerType(ownerType);
        entity.setOwnerId(ownerId);
        entity.setExpiresAt(now.plus(ttlMinutes, ChronoUnit.MINUTES));
        entity.setUsed(false);
        passwordResetTokenRepository.save(entity);
        return rawToken;
    }

    private ForgotPasswordResponse buildForgotPasswordResponse(String message, long ttlMinutes, String rawToken) {
        if (appProperties.getSecurity().isExposeResetTokenInResponse()) {
            return new ForgotPasswordResponse(message, ttlMinutes, rawToken, "/reset-password?token=" + rawToken);
        }
        return new ForgotPasswordResponse(message, ttlMinutes, null, null);
    }

    public static UserInfoResponse toStudentUserInfo(StudentProfile profile) {
        return new UserInfoResponse(
                profile.getPseudonymId(),
                OwnerType.STUDENT,
                Role.ROLE_STUDENT,
                profile.getPseudonymHandle(),
                profile.getAvatarCode(),
                null,
                null,
                null,
                profile.getBio(),
                profile.getCreatedAt()
        );
    }

    public static UserInfoResponse toStaffUserInfo(StaffUser staff) {
        return new UserInfoResponse(
                staff.getStaffId(),
                OwnerType.STAFF,
                staff.getRole(),
                staff.getFullName(),
                null,
                staff.getEmail(),
                staff.getMobileNumber(),
                staff.isOnCall(),
                staff.getBio(),
                staff.getCreatedAt()
        );
    }
}
