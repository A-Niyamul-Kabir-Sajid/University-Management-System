package com.example.ums;

import org.junit.jupiter.api.Test;
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
@SpringBootTest
@AutoConfigureMockMvc
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    // I1
    @Test
    void anonymousGets401_andTeacherIsForbiddenFromStudents() throws Exception {
        mvc.perform(get("/api/students")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/students").with(user("t@test.com").roles("TEACHER")))
                .andExpect(status().isForbidden());
    }

    // I2
    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCreatesTeacher_thenTeacherCanLogIn() throws Exception {
        String email = uniqueEmail();
        String body = """
                {"name":"Dr. Rahman","email":"%s","password":"secret123","department":"CSE"}
                """.formatted(email);

        mvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TEACHER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("TEACHER"));
    }

    // I3
    @Test
    @WithMockUser(roles = "ADMIN")
    void countEndpoint_returnsNumber_andMissingIdReturns404() throws Exception {
        mvc.perform(get("/api/teachers/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").isNumber());

        mvc.perform(get("/api/teachers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
