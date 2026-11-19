package edu.cit.pescante.mindhaven.dashboard;

import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.security.EmailHashService;
import edu.cit.pescante.mindhaven.user.AuditLog;
import edu.cit.pescante.mindhaven.user.AuditLogRepository;
import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.OwnerType;
import edu.cit.pescante.mindhaven.user.RefreshTokenRepository;
import edu.cit.pescante.mindhaven.user.Role;
import edu.cit.pescante.mindhaven.user.StaffUser;
import edu.cit.pescante.mindhaven.user.StaffUserRepository;
import edu.cit.pescante.mindhaven.user.StudentAccountRepository;
import edu.cit.pescante.mindhaven.user.TopicRoom;
import edu.cit.pescante.mindhaven.user.TopicRoomRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final StaffUserRepository staffUserRepository;
    private final StudentAccountRepository studentAccountRepository;
    private final TopicRoomRepository topicRoomRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailHashService emailHashService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public AdminDashboardController(
            StaffUserRepository staffUserRepository,
            StudentAccountRepository studentAccountRepository,
            TopicRoomRepository topicRoomRepository,
            AuditLogRepository auditLogRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailHashService emailHashService,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        this.staffUserRepository = staffUserRepository;
        this.studentAccountRepository = studentAccountRepository;
        this.topicRoomRepository = topicRoomRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailHashService = emailHashService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDashboard() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalStudents", studentAccountRepository.count());
        metrics.put("totalCounselors", staffUserRepository.countByRole(Role.ROLE_COUNSELOR));
        metrics.put("onCallCounselors", staffUserRepository.countByRoleAndOnCallTrueAndActiveTrue(Role.ROLE_COUNSELOR));
        metrics.put("totalAdmins", staffUserRepository.countByRole(Role.ROLE_ADMIN));
        metrics.put("activeTopicRooms", topicRoomRepository.countByActiveTrue());

        List<Map<String, Object>> staff = staffUserRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toStaffView)
                .toList();

        List<Map<String, Object>> rooms = topicRoomRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toRoomView)
                .toList();

        List<Map<String, Object>> logs = auditLogRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toAuditView)
                .toList();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("metrics", metrics);
        payload.put("staffUsers", staff);
        payload.put("topicRooms", rooms);
        payload.put("auditLogs", logs);
        return ResponseEntity.ok(payload);
    }

    @PostMapping("/staff")
    @Transactional
    public ResponseEntity<Map<String, Object>> createStaff(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateStaffRequest request
    ) {
        if (request.role() != Role.ROLE_COUNSELOR && request.role() != Role.ROLE_ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Staff accounts must have role ROLE_COUNSELOR or ROLE_ADMIN.");
        }

        String normalizedEmail = emailHashService.normalizeEmail(request.email());
        String emailHash = emailHashService.hmacEmail(normalizedEmail);
        if (staffUserRepository.existsByEmailIgnoreCase(normalizedEmail)
                || studentAccountRepository.existsByEmailHash(emailHash)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists.");
        }

        StaffUser staff = new StaffUser();
        staff.setFullName(request.fullName().trim());
        staff.setEmail(normalizedEmail);
        staff.setPasswordHash(passwordEncoder.encode(request.password()));
        staff.setRole(request.role());
        String mobile = request.mobileNumber() == null ? "" : request.mobileNumber().trim();
        staff.setMobileNumber(mobile.isEmpty() ? null : mobile);
        staff.setOnCall(request.role() == Role.ROLE_COUNSELOR && request.onCall());
        staff.setActive(true);
        staff = staffUserRepository.save(staff);

        auditLogService.record(
                Role.ROLE_ADMIN.name(),
                currentUser.displayName(),
                "STAFF_CREATED",
                "Created " + staff.getRole().name() + " account for " + staff.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toStaffView(staff));
    }

    @PatchMapping("/staff/{staffId}/status")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateStaffStatus(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID staffId,
            @RequestBody UpdateStaffStatusRequest request
    ) {
        StaffUser staff = staffUserRepository.findById(staffId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Staff user not found."));

        if (request.active() != null) {
            if (!request.active() && staff.getStaffId().equals(currentUser.id())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot deactivate your own administrator account.");
            }
            staff.setActive(request.active());
            if (!request.active()) {
                staff.setOnCall(false);
                refreshTokenRepository.revokeAllActiveByOwner(OwnerType.STAFF, staff.getStaffId());
            }
        }

        if (request.onCall() != null) {
            if (staff.getRole() != Role.ROLE_COUNSELOR && request.onCall()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only counselor accounts can be placed on-call.");
            }
            staff.setOnCall(request.onCall());
        }

        staff = staffUserRepository.save(staff);

        auditLogService.record(
                Role.ROLE_ADMIN.name(),
                currentUser.displayName(),
                "STAFF_UPDATED",
                "Updated " + staff.getEmail() + " (active=" + staff.isActive() + ", onCall=" + staff.isOnCall() + ")."
        );

        return ResponseEntity.ok(toStaffView(staff));
    }

    @PostMapping("/topic-rooms")
    @Transactional
    public ResponseEntity<Map<String, Object>> createTopicRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateTopicRoomRequest request
    ) {
        String roomName = request.roomName().trim();
        String slug = slugify(roomName);

        if (topicRoomRepository.existsByRoomNameIgnoreCase(roomName)
                || topicRoomRepository.existsByCategorySlugIgnoreCase(slug)) {
            throw new ApiException(HttpStatus.CONFLICT, "A topic room with this name already exists.");
        }

        TopicRoom room = new TopicRoom();
        room.setRoomName(roomName);
        room.setCategorySlug(slug);
        room.setDescription(request.description().trim());
        room.setActive(true);
        room.setCreatedBy(currentUser.id());
        room = topicRoomRepository.save(room);

        auditLogService.record(
                Role.ROLE_ADMIN.name(),
                currentUser.displayName(),
                "TOPIC_ROOM_CREATED",
                "Created topic chatroom category: " + room.getRoomName()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(toRoomView(room));
    }

    @PatchMapping("/topic-rooms/{roomId}/toggle")
    @Transactional
    public ResponseEntity<Map<String, Object>> toggleTopicRoom(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @PathVariable UUID roomId
    ) {
        TopicRoom room = topicRoomRepository.findById(roomId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Topic room not found."));

        room.setActive(!room.isActive());
        room = topicRoomRepository.save(room);

        auditLogService.record(
                Role.ROLE_ADMIN.name(),
                currentUser.displayName(),
                "TOPIC_ROOM_UPDATED",
                "Set topic room '" + room.getRoomName() + "' active=" + room.isActive()
        );

        return ResponseEntity.ok(toRoomView(room));
    }

    private String slugify(String input) {
        String slug = input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "room-" + UUID.randomUUID().toString().substring(0, 6) : slug;
    }

    private Map<String, Object> toStaffView(StaffUser staff) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("staffId", staff.getStaffId());
        map.put("fullName", staff.getFullName());
        map.put("email", staff.getEmail());
        map.put("role", staff.getRole());
        map.put("mobileNumber", staff.getMobileNumber());
        map.put("onCall", staff.isOnCall());
        map.put("active", staff.isActive());
        map.put("createdAt", staff.getCreatedAt());
        return map;
    }

    private Map<String, Object> toRoomView(TopicRoom room) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roomId", room.getRoomId());
        map.put("roomName", room.getRoomName());
        map.put("categorySlug", room.getCategorySlug());
        map.put("description", room.getDescription());
        map.put("active", room.isActive());
        map.put("createdAt", room.getCreatedAt());
        return map;
    }

    private Map<String, Object> toAuditView(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("auditId", log.getAuditId());
        map.put("actorRole", log.getActorRole());
        map.put("actorLabel", log.getActorLabel());
        map.put("action", log.getAction());
        map.put("details", log.getDetails());
        map.put("createdAt", log.getCreatedAt());
        return map;
    }
}
