package edu.cit.pescante.mindhaven.auth;

import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.config.AppProperties;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.security.EmailHashService;
import edu.cit.pescante.mindhaven.security.JwtService;
import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.OwnerType;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    public record AuthSessionResult(AuthResponse response, String rawRefreshToken) {
    }

    private final StudentAccountRepository studentAccountRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StaffUserRepository staffUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
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
                staff.getCreatedAt()
        );
    }
}
