package edu.cit.pescante.mindhaven.dashboard;

import edu.cit.pescante.mindhaven.auth.AuthService;
import edu.cit.pescante.mindhaven.auth.UserInfoResponse;
import edu.cit.pescante.mindhaven.common.ApiException;
import edu.cit.pescante.mindhaven.security.AuthenticatedUser;
import edu.cit.pescante.mindhaven.user.AuditLogService;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/counselor")
@PreAuthorize("hasRole('COUNSELOR')")
public class CounselorDashboardController {

    private final StaffUserRepository staffUserRepository;
    private final StudentAccountRepository studentAccountRepository;
    private final TopicRoomRepository topicRoomRepository;
    private final AuditLogService auditLogService;

    public CounselorDashboardController(
            StaffUserRepository staffUserRepository,
            StudentAccountRepository studentAccountRepository,
            TopicRoomRepository topicRoomRepository,
            AuditLogService auditLogService
    ) {
        this.staffUserRepository = staffUserRepository;
        this.studentAccountRepository = studentAccountRepository;
        this.topicRoomRepository = topicRoomRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/dashboard")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getDashboard(@AuthenticationPrincipal AuthenticatedUser currentUser) {
        StaffUser counselor = staffUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Counselor account not found."));

        List<Map<String, Object>> rooms = topicRoomRepository.findAllByActiveTrueOrderByRoomNameAsc().stream()
                .map(this::toRoomView)
                .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeStudents", studentAccountRepository.countByActiveTrue());
        summary.put("onCallCounselors", staffUserRepository.countByRoleAndOnCallTrueAndActiveTrue(Role.ROLE_COUNSELOR));
        summary.put("activeTopicRooms", rooms.size());
        summary.put("openCrisisIncidents", 0);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("counselor", AuthService.toStaffUserInfo(counselor));
        payload.put("summary", summary);
        payload.put("topicRooms", rooms);
        return ResponseEntity.ok(payload);
    }

    @PatchMapping("/on-call")
    @Transactional
    public ResponseEntity<UserInfoResponse> updateOnCallStatus(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody UpdateCounselorOnCallRequest request
    ) {
        StaffUser counselor = staffUserRepository.findById(currentUser.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Counselor account not found."));

        counselor.setOnCall(request.onCall());
        if (request.mobileNumber() != null) {
            String trimmed = request.mobileNumber().trim();
            counselor.setMobileNumber(trimmed.isEmpty() ? null : trimmed);
        }
        counselor = staffUserRepository.save(counselor);

        auditLogService.record(
                Role.ROLE_COUNSELOR.name(),
                counselor.getFullName(),
                "ON_CALL_UPDATED",
                "Counselor set on-call status to " + counselor.isOnCall() + "."
        );

        return ResponseEntity.ok(AuthService.toStaffUserInfo(counselor));
    }

    private Map<String, Object> toRoomView(TopicRoom room) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("roomId", room.getRoomId());
        map.put("roomName", room.getRoomName());
        map.put("categorySlug", room.getCategorySlug());
        map.put("description", room.getDescription());
        return map;
    }
}
