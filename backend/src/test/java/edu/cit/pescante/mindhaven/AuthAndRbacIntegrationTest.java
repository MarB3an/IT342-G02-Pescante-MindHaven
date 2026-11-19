package edu.cit.pescante.mindhaven;

import edu.cit.pescante.mindhaven.user.StudentAccount;
import edu.cit.pescante.mindhaven.user.StudentAccountRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
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
class AuthAndRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StudentAccountRepository studentAccountRepository;

    private String extractAccessToken(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        assertThat(matcher.find()).as("accessToken should be present in response: " + body).isTrue();
        return matcher.group(1);
    }

    @Test
    void fullIncrement1AuthAndRbacFlowWorksEndToEnd() throws Exception {
        // 1. Unauthenticated access to protected endpoints returns 401
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/student/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/counselor/dashboard"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized());

        // 2. Validation rejects weak password and non-campus email
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"student@gmail.com","password":"Password1","confirmPassword":"Password1"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"weak@cit.edu","password":"short","confirmPassword":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        // 3. Valid student registration succeeds with hashed password and HMAC email hash
        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"juan.delacruz@cit.edu","password":"StudentPass1!","confirmPassword":"StudentPass1!","preferredAvatar":"lotus"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("ROLE_STUDENT"))
                .andExpect(jsonPath("$.user.avatarCode").value("lotus"))
                .andExpect(jsonPath("$.user.email").doesNotExist())
                .andReturn();

        String studentToken = extractAccessToken(registerResult);
        Cookie refreshCookie = registerResult.getResponse().getCookie("mindhaven_refresh_token");
        assertThat(refreshCookie).isNotNull();
        assertThat(refreshCookie.isHttpOnly()).isTrue();

        // Verify password is BCrypt hashed and email is NOT stored in plaintext
        List<StudentAccount> accounts = studentAccountRepository.findAll();
        assertThat(accounts).hasSize(1);
        StudentAccount stored = accounts.get(0);
        assertThat(stored.getPasswordHash()).startsWith("$2");
        assertThat(stored.getPasswordHash()).doesNotContain("StudentPass1!");
        assertThat(stored.getEmailHash()).hasSize(64);
        assertThat(stored.getEmailHash()).doesNotContain("juan.delacruz");

        // 4. Duplicate student registration returns 409 Conflict
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"JUAN.DELACRUZ@cit.edu","password":"StudentPass1!","confirmPassword":"StudentPass1!"}
                                """))
                .andExpect(status().isConflict());

        // 5. Student RBAC: allowed on /student/**, forbidden (403) on /counselor/** and /admin/**
        mockMvc.perform(get("/api/v1/student/dashboard")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.privacyStatus.pseudonymousMode").value(true));

        mockMvc.perform(get("/api/v1/counselor/dashboard")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        // 6. Invalid login returns 401
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"juan.delacruz@cit.edu","password":"WrongPassword1!"}
                                """))
                .andExpect(status().isUnauthorized());

        // 7. Refresh token rotation and logout revocation
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_STUDENT"))
                .andReturn();
        Cookie rotatedCookie = refreshResult.getResponse().getCookie("mindhaven_refresh_token");
        assertThat(rotatedCookie).isNotNull();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + studentToken)
                        .cookie(rotatedCookie))
                .andExpect(status().isOk());

        // Revoked refresh token cannot restore session
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(rotatedCookie))
                .andExpect(status().isUnauthorized());

        // 8. Bootstrapped Admin login -> creates Counselor -> Counselor login & RBAC verification
        MvcResult adminLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@cit.edu","password":"AdminPassword1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_ADMIN"))
                .andReturn();
        String adminToken = extractAccessToken(adminLogin);

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.totalStudents").value(1));

        mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName":"Dr. Maria Santos",
                                  "email":"maria.santos@cit.edu",
                                  "password":"CounselorPass1!",
                                  "role":"ROLE_COUNSELOR",
                                  "mobileNumber":"+639171234567",
                                  "onCall":true
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("ROLE_COUNSELOR"));

        MvcResult counselorLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"maria.santos@cit.edu","password":"CounselorPass1!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("ROLE_COUNSELOR"))
                .andReturn();
        String counselorToken = extractAccessToken(counselorLogin);

        mockMvc.perform(get("/api/v1/counselor/dashboard")
                        .header("Authorization", "Bearer " + counselorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.activeStudents").value(1));

        mockMvc.perform(patch("/api/v1/counselor/on-call")
                        .header("Authorization", "Bearer " + counselorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"onCall":false,"mobileNumber":"+639171234567"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onCall").value(false));

        mockMvc.perform(get("/api/v1/admin/dashboard")
                        .header("Authorization", "Bearer " + counselorToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/student/dashboard")
                        .header("Authorization", "Bearer " + counselorToken))
                .andExpect(status().isForbidden());
    }
}
