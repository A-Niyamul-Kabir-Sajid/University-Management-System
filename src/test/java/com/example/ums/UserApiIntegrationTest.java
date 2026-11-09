package com.example.ums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integration tests: the whole app (security, controllers, service, H2 database) runs together. */
@DisplayName("Integration Test: User API (security + controller + service + H2)")
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    // I1: Anonymous gets 401, teacher gets 403 on students
    /**
     * I1 - Security rules (SecurityConfig) working together with the real filter chain.
     * Checks: (1) no login at all -> 401 Unauthorized,
     *         (2) logged in as TEACHER but asking for the students resource -> 403 Forbidden.
     */
    @Test
    @DisplayName("I1: Anonymous gets 401, teacher gets 403 on students")
    void anonymousGets401_andTeacherIsForbiddenFromStudents() throws Exception {
        // No user attached to the request: Spring Security must answer 401.
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());

        // user(...).roles("TEACHER") fakes a logged-in teacher; /api/students only allows ADMIN and STUDENT.
        mvc.perform(get("/api/students").with(user("t@test.com").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    // I2: Admin creates teacher, teacher can log in
    /**
     * I2 - Full path: Controller -> Service -> BCrypt -> H2 database -> AuthController.
     * An ADMIN creates a teacher, then that teacher logs in with the password that was just set.
     * This proves the password is stored hashed AND can still be verified at login.
     */
    @Test
    @DisplayName("I2: Admin creates teacher, teacher can log in")
    @WithMockUser(roles = "ADMIN")   // the request below runs as a logged-in admin
    void adminCreatesTeacher_thenTeacherCanLogIn() throws Exception {
        String email = uniqueEmail();   // random, so reruns never hit "email already in use"
        String body = """
                {"name":"Dr. Rahman","email":"%s","password":"secret123","department":"CSE"}
                """.formatted(email);

        // Step 1: create the teacher. Expect 201 Created, role TEACHER, and no password in the response.
        mvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        // Step 2: log in with the new credentials. Expect 200 OK and the same role back.
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    // I3: Count endpoint works, missing id returns JSON 404
    /**
     * I3 - New /count endpoint plus the JSON error handling (GlobalExceptionHandler).
     * Checks: (1) GET /api/teachers/count returns {"count": <number>},
     *         (2) asking for an id that does not exist returns a JSON 404, not an HTML error page.
     */
    @Test
    @DisplayName("I3: Count endpoint works, missing id returns JSON 404")
    @WithMockUser(roles = "ADMIN")
    void countEndpoint_returnsNumber_andMissingIdReturns404() throws Exception {
        // "count" is matched before "/{id}", so it is not treated as an id.
        mvc.perform(get("/api/teachers/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());

        // ResourceNotFoundException -> GlobalExceptionHandler -> 404 with a JSON body containing "status".
        mvc.perform(get("/api/teachers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
