package edu.cit.pescante.mindhaven.dashboard;

import edu.cit.pescante.mindhaven.auth.AuthService;
import edu.cit.pescante.mindhaven.auth.UserInfoResponse;
import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.PseudonymGenerator;
import edu.cit.pescante.mindhaven.user.Role;
import edu.cit.pescante.mindhaven.user.StudentProfile;
import edu.cit.pescante.mindhaven.user.StudentProfileRepository;
import edu.cit.pescante.mindhaven.user.TopicRoom;
import edu.cit.pescante.mindhaven.user.TopicRoomRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentDashboardController {

    private final StudentProfileRepository studentProfileRepository;
    private final TopicRoomRepository topicRoomRepository;
    private final PseudonymGenerator pseudonymGenerator;
    private final AuditLogService auditLogService;

    public StudentDashboardController(
            StudentProfileRepository studentProfileRepository,
            TopicRoomRepository topicRoomRepository,
            PseudonymGenerator pseudonymGenerator,
            AuditLogService auditLogService
    ) {
        this.studentProfileRepository = studentProfileRepository;
        this.topicRoomRepository = topicRoomRepository;
        this.pseudonymGenerator = pseudonymGenerator;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        StudentProfile profile = studentProfileRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));

        List<Map<String, Object>> rooms = topicRoomRepository.findAllByActiveTrueOrderByRoomNameAsc().stream()
                .map(this::toRoomView)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("profile", AuthService.toStudentUserInfo(profile));
        payload.put("availableAvatars", PseudonymGenerator.VALID_AVATARS.stream().sorted().toList());
        payload.put("activeTopicRooms", rooms);
        payload.put("privacyStatus", Map.of(
                "pseudonymousMode", true,
                "emailStoredAsPlaintext", false,
                "hashingAlgorithm", "HMAC-SHA256 + BCrypt-12"
        ));
        return ResponseEntity.ok(payload);
    }

    @PatchMapping("/profile")
    @Transactional
    public ResponseEntity<UserInfoResponse> updateProfile(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        StudentProfile profile = studentProfileRepository.findById(currentUser.id())
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

        profile.setLastSeenAt(Instant.now());
        profile = studentProfileRepository.save(profile);

        auditLogService.record(
                Role.ROLE_STUDENT.name(),
                profile.getPseudonymHandle(),
                "PROFILE_UPDATED",
                "Pseudonymous profile updated (avatar=" + profile.getAvatarCode() + ")."
        );

        return ResponseEntity.ok(AuthService.toStudentUserInfo(profile));
    }

    @PostMapping("/profile/regenerate-pseudonym")
    @Transactional
    public ResponseEntity<UserInfoResponse> regeneratePseudonym(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        StudentProfile profile = studentProfileRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student profile not found."));

        String newHandle = pseudonymGenerator.generateUniqueHandle();
        profile.setPseudonymHandle(newHandle);
        profile.setLastSeenAt(Instant.now());
        profile = studentProfileRepository.save(profile);

        auditLogService.record(
                Role.ROLE_STUDENT.name(),
                newHandle,
                "PSEUDONYM_REGENERATED",
                "Student generated a new randomized pseudonym handle."
        );

        return ResponseEntity.ok(AuthService.toStudentUserInfo(profile));
    }

    private Map<String, Object> toRoomView(TopicRoom room) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roomId", room.getRoomId());
        map.put("roomName", room.getRoomName());
        map.put("categorySlug", room.getCategorySlug());
        map.put("description", room.getDescription());
        map.put("active", room.isActive());
        return map;
    }
}
