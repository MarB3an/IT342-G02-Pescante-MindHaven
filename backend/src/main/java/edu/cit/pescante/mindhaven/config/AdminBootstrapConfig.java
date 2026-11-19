package edu.cit.pescante.mindhaven.config;

import edu.cit.pescante.mindhaven.user.AuditLogService;
import edu.cit.pescante.mindhaven.user.Role;
import edu.cit.pescante.mindhaven.user.StaffUser;
import edu.cit.pescante.mindhaven.user.StaffUserRepository;
import edu.cit.pescante.mindhaven.user.TopicRoom;
import edu.cit.pescante.mindhaven.user.TopicRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
public class AdminBootstrapConfig {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapConfig.class);

    @Bean
    ApplicationRunner bootstrapInitialData(
            AppProperties appProperties,
            StaffUserRepository staffUserRepository,
            TopicRoomRepository topicRoomRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService
    ) {
        return args -> {
            String adminEmail = appProperties.getBootstrap().getAdminEmail();
            String adminPassword = appProperties.getBootstrap().getAdminPassword();
            String adminFullName = appProperties.getBootstrap().getAdminFullName();

            if (adminEmail != null && !adminEmail.isBlank()
                    && adminPassword != null && !adminPassword.isBlank()) {
                String normalizedEmail = adminEmail.trim().toLowerCase();
                if (!staffUserRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                    StaffUser admin = new StaffUser();
                    admin.setEmail(normalizedEmail);
                    admin.setPasswordHash(passwordEncoder.encode(adminPassword));
                    admin.setFullName(adminFullName != null && !adminFullName.isBlank()
                            ? adminFullName.trim() : "System Administrator");
                    admin.setRole(Role.ROLE_ADMIN);
                    admin.setActive(true);
                    admin.setOnCall(false);
                    staffUserRepository.save(admin);

                    auditLogService.record(
                            "SYSTEM",
                            "Bootstrap",
                            "ADMIN_BOOTSTRAPPED",
                            "Initial ROLE_ADMIN account provisioned from environment configuration (" + normalizedEmail + ")"
                    );
                    log.info("Bootstrapped initial ROLE_ADMIN account for {}", normalizedEmail);
                }
            } else {
                log.info("ADMIN_EMAIL / ADMIN_PASSWORD not configured; skipping initial admin bootstrap.");
            }

            if (topicRoomRepository.count() == 0) {
                List<String[]> defaults = List.of(
                        new String[]{"Academic & Thesis Pressure", "academic-thesis-pressure",
                                "Supportive peer space for exam stress, deadlines, thesis burnout, and coursework overwhelm."},
                        new String[]{"First-Year Transition", "first-year-transition",
                                "Peer discussions for adjusting to university life, new routines, and campus belonging."},
                        new String[]{"Isolation & Burnout", "isolation-burnout",
                                "A safe, stigma-free room to talk through loneliness, fatigue, and emotional exhaustion."}
                );
                for (String[] def : defaults) {
                    if (!topicRoomRepository.existsByCategorySlugIgnoreCase(def[1])) {
                        TopicRoom room = new TopicRoom();
                        room.setRoomName(def[0]);
                        room.setCategorySlug(def[1]);
                        room.setDescription(def[2]);
                        room.setActive(true);
                        topicRoomRepository.save(room);
                    }
                }
                log.info("Initialized default MindHaven SRS topic room categories.");
            }
        };
    }
}
