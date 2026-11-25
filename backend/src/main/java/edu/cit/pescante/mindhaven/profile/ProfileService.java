package edu.cit.pescante.mindhaven.profile;

import edu.cit.pescante.mindhaven.auth.AuthService;
import edu.cit.pescante.mindhaven.auth.UserInfoResponse;
import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.OwnerType;
import edu.cit.pescante.mindhaven.user.PseudonymGenerator;
import edu.cit.pescante.mindhaven.user.Role;
import edu.cit.pescante.mindhaven.user.StaffUser;
import edu.cit.pescante.mindhaven.user.StaffUserRepository;
import edu.cit.pescante.mindhaven.user.StudentProfile;
import edu.cit.pescante.mindhaven.user.StudentProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final StaffUserRepository staffUserRepository;
    private final PseudonymGenerator pseudonymGenerator;
    private final AuditLogService auditLogService;

    public ProfileService(
            StudentProfileRepository studentProfileRepository,
            StaffUserRepository staffUserRepository,
            PseudonymGenerator pseudonymGenerator,
            AuditLogService auditLogService
    ) {
        this.studentProfileRepository = studentProfileRepository;
        this.staffUserRepository = staffUserRepository;
        this.pseudonymGenerator = pseudonymGenerator;
        this.auditLogService = auditLogService;
    }

    public void assertSelfOwnership(AuthenticatedUser currentUser, UUID targetUserId) {
        if (currentUser == null || targetUserId == null || !currentUser.id().equals(targetUserId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You are not authorized to access or modify another user's profile.");
        }
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getOwnProfile(AuthenticatedUser currentUser) {
        if (currentUser.ownerType() == OwnerType.STUDENT) {
            StudentProfile profile = studentProfileRepository.findById(currentUser.id())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));
            return AuthService.toStudentUserInfo(profile);
        }

        StaffUser staff = staffUserRepository.findById(currentUser.id())
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff profile not found."));
        return AuthService.toStaffUserInfo(staff);
    }

    @Transactional
    public UserInfoResponse updateOwnProfile(AuthenticatedUser currentUser, UpdateSelfProfileRequest request) {
        if (currentUser.ownerType() == OwnerType.STUDENT) {
            return updateStudentProfile(currentUser.id(), request);
        }
        return updateStaffProfile(currentUser.id(), request);
    }

    private UserInfoResponse updateStudentProfile(UUID pseudonymId, UpdateSelfProfileRequest request) {
        StudentProfile profile = studentProfileRepository.findById(pseudonymId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));

        String oldHandle = profile.getPseudonymHandle();

        if (request.pseudonymHandle() != null && !request.pseudonymHandle().isBlank()) {
            String trimmed = request.pseudonymHandle().trim();
            String validationError = pseudonymGenerator.validateCustomHandle(trimmed);
            if (validationError != null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, validationError);
            }
            if (!trimmed.equalsIgnoreCase(oldHandle)
                    && studentProfileRepository.existsByPseudonymHandleIgnoreCase(trimmed)) {
                throw new ApiException(HttpStatus.CONFLICT, "That pseudonym handle is already taken. Please choose another.");
            }
            profile.setPseudonymHandle(trimmed);
        }

        if (request.avatarCode() != null && !request.avatarCode().isBlank()) {
            String avatar = request.avatarCode().trim().toLowerCase();
            if (!PseudonymGenerator.VALID_AVATARS.contains(avatar)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported avatar selection.");
            }
            profile.setAvatarCode(avatar);
        }

        if (request.bio() != null) {
            String trimmedBio = request.bio().trim();
            if (!trimmedBio.isEmpty()) {
                String bioError = pseudonymGenerator.validateBio(trimmedBio);
                if (bioError != null) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, bioError);
                }
                profile.setBio(trimmedBio);
            } else {
                profile.setBio(null);
            }
        }

        profile.setLastSeenAt(Instant.now());
        profile = studentProfileRepository.save(profile);

        auditLogService.record(
                Role.ROLE_STUDENT.name(),
                profile.getPseudonymHandle(),
                "PROFILE_UPDATED",
                "Student updated own pseudonymous profile."
        );

        return AuthService.toStudentUserInfo(profile);
    }

    private UserInfoResponse updateStaffProfile(UUID staffId, UpdateSelfProfileRequest request) {
        StaffUser staff = staffUserRepository.findById(staffId)
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff profile not found."));

        if (request.fullName() != null) {
            String trimmedName = request.fullName().trim();
            if (trimmedName.length() < 2 || trimmedName.length() > 120) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Full name must be between 2 and 120 characters.");
            }
            staff.setFullName(trimmedName);
        }

        if (request.mobileNumber() != null) {
            String trimmedMobile = request.mobileNumber().trim();
            staff.setMobileNumber(trimmedMobile.isEmpty() ? null : trimmedMobile);
        }

        if (request.bio() != null) {
            String trimmedBio = request.bio().trim();
            staff.setBio(trimmedBio.isEmpty() ? null : trimmedBio);
        }

        if (request.onCall() != null) {
            if (staff.getRole() != Role.ROLE_COUNSELOR && request.onCall()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only counselor accounts can be placed on-call.");
            }
            if (staff.getRole() == Role.ROLE_COUNSELOR) {
                staff.setOnCall(request.onCall());
            }
        }

        staff = staffUserRepository.save(staff);

        auditLogService.record(
                staff.getRole().name(),
                staff.getFullName(),
                "PROFILE_UPDATED",
                "Staff user updated own profile details."
        );

        return AuthService.toStaffUserInfo(staff);
    }
}
