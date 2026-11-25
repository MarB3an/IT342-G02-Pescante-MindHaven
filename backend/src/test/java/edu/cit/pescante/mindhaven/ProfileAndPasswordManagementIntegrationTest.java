package edu.cit.pescante.mindhaven;

import edu.cit.pescante.mindhaven.user.PasswordResetToken;
import edu.cit.pescante.mindhaven.user.PasswordResetTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileAndPasswordManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    private String extractJsonField(MvcResult result, String field) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).as(field + " should be present in response: " + body).isTrue();
        return matcher.group(1);
    }

    @Test
    void fullIncrement2ProfileChangeAndResetPasswordFlowWorksEndToEnd() throws Exception {
        // 1. Unauthenticated profile & change-password requests return 401
        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"Old1!","newPassword":"NewPassword1!","confirmPassword":"NewPassword1!"}
                                """))
                .andExpect(status().isUnauthorized());

        // 2. Register two students to test self-profile editing and cross-user 403 isolation
        MvcResult student1Reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu","password":"InitialPass1!","confirmPassword":"InitialPass1!","preferredAvatar":"sparrow"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String student1Token = extractJsonField(student1Reg, "accessToken");
        String student1Id = extractJsonField(student1Reg, "id");

        MvcResult student2Reg = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student2@cit.edu","password":"InitialPass1!","confirmPassword":"InitialPass1!","preferredAvatar":"lotus"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String student2Id = extractJsonField(student2Reg, "id");

        // 3. Student 1 views and edits own profile (handle, avatar, bio)
        mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_STUDENT"));

        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pseudonymHandle":"CalmHarbor77","avatarCode":"aurora","bio":"Taking one step at a time."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("CalmHarbor77"))
                .andExpect(jsonPath("$.avatarCode").value("aurora"))
                .andExpect(jsonPath("$.bio").value("Taking one step at a time."));

        // Student bio containing PII (email) is rejected
        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Reach me at myname@cit.edu"}
                                """))
                .andExpect(status().isBadRequest());

        // 4. Prevent unauthorized access/modification to another user's profile (403 Forbidden)
        mockMvc.perform(get("/api/v1/profile/" + student2Id)
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/profile/" + student2Id)
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bio":"Hacked bio"}
                                """))
                .andExpect(status().isForbidden());

        // Accessing own profile by explicit ID succeeds
        mockMvc.perform(get("/api/v1/profile/" + student1Id)
                        .header("Authorization", "Bearer " + student1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("CalmHarbor77"));

        // 5. Staff (Admin) views and edits own profile
        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@cit.edu","password":"AdminPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String adminToken = extractJsonField(adminLogin, "accessToken");

        mockMvc.perform(patch("/api/v1/profile")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Chief System Administrator","mobileNumber":"+639170001122","bio":"Office hours 8AM-5PM"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Chief System Administrator"))
                .andExpect(jsonPath("$.mobileNumber").value("+639170001122"))
                .andExpect(jsonPath("$.bio").value("Office hours 8AM-5PM"));

        // Admin cannot edit another user's profile via /api/v1/profile/{userId}
        mockMvc.perform(patch("/api/v1/profile/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Unauthorized Change"}
                                """))
                .andExpect(status().isForbidden());

        // 6. Change Password: wrong current password rejected, same password rejected, valid succeeds
        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"WrongCurrent1!","newPassword":"ChangedPass1!","confirmPassword":"ChangedPass1!"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"InitialPass1!","newPassword":"InitialPass1!","confirmPassword":"InitialPass1!"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + student1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"InitialPass1!","newPassword":"ChangedPass1!","confirmPassword":"ChangedPass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());

        // Old password must no longer work; new password must work
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu","password":"InitialPass1!"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu","password":"ChangedPass1!"}
                                """))
                .andExpect(status().isOk());

        // 7. Forgot Password & Single-Use Expiring Reset Token
        // Unknown email returns 200 without leaking a token
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"nonexistent@cit.edu"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").doesNotExist());

        // Known student email generates a single-use token stored SHA-256 hashed
        MvcResult forgotRes = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resetToken").exists())
                .andReturn();
        String resetToken = extractJsonField(forgotRes, "resetToken");

        List<PasswordResetToken> storedTokens = passwordResetTokenRepository.findAll();
        assertThat(storedTokens).isNotEmpty();
        assertThat(storedTokens).allSatisfy(t -> {
            assertThat(t.getTokenHash()).hasSize(64);
            assertThat(t.getTokenHash()).doesNotContain(resetToken);
        });

        // Validate endpoint reports valid=true
        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", resetToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));

        // Reset password succeeds
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"RecoveredPass1!","confirmPassword":"RecoveredPass1!"}
                                """.formatted(resetToken)))
                .andExpect(status().isOk());

        // Single-use enforcement: reusing the same token fails
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"AnotherPass1!","confirmPassword":"AnotherPass1!"}
                                """.formatted(resetToken)))
                .andExpect(status().isBadRequest());

        // Previous password fails; recovered password succeeds
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu","password":"ChangedPass1!"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu","password":"RecoveredPass1!"}
                                """))
                .andExpect(status().isOk());

        // 8. Expired token is rejected
        MvcResult forgotRes2 = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"inc2.student1@cit.edu"}
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String expiredRawToken = extractJsonField(forgotRes2, "resetToken");

        passwordResetTokenRepository.findAll().forEach(t -> {
            if (!t.isUsed()) {
                t.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
                passwordResetTokenRepository.save(t);
            }
        });

        mockMvc.perform(get("/api/v1/auth/reset-password/validate")
                        .param("token", expiredRawToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"ShouldFail1!","confirmPassword":"ShouldFail1!"}
                                """.formatted(expiredRawToken)))
                .andExpect(status().isBadRequest());
    }
}
